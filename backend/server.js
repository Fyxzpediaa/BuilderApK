require('dotenv').config();
const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const TelegramBot = require('node-telegram-bot-api');
const crypto = require('crypto');

const { TELEGRAM_BOT_TOKEN, ADMIN_IDS, PORT = 8080 } = process.env;
if (!TELEGRAM_BOT_TOKEN || TELEGRAM_BOT_TOKEN === 'PUT_YOUR_BOT_TOKEN_HERE') throw new Error('Set TELEGRAM_BOT_TOKEN in backend/.env');
const admins = new Set((ADMIN_IDS || '').split(',').map(s => s.trim()).filter(Boolean));
if (!admins.size) throw new Error('Set at least one numeric Telegram user ID in ADMIN_IDS');

const app = express();
app.disable('x-powered-by');
app.use(helmet());
app.use(express.json({ limit: '16kb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 90, standardHeaders: 'draft-7', legacyHeaders: false }));
const bot = new TelegramBot(TELEGRAM_BOT_TOKEN, { polling: true });
const devices = new Map();
const pairCodes = new Map();
const commandIndex = new Map();
const deviceCommands = new Map();
const pairedChats = new Map();
const now = () => Date.now();
const random = n => crypto.randomBytes(n).toString('hex');
const admin = msg => admins.has(String(msg.from?.id || ''));
const menu = { inline_keyboard: [
  [{ text: '📊 Device status', callback_data: 'status' }],
  [{ text: '📷 Open camera', callback_data: 'camera' }],
  [{ text: '🤳 Front camera', callback_data: 'camera_front' }, { text: '📸 Rear camera', callback_data: 'camera_rear' }],
  [{ text: '🔦 Flash ON', callback_data: 'flash_on' }, { text: 'Flash OFF', callback_data: 'flash_off' }]
]};
async function showMenu(chatId) {
  return bot.sendMessage(chatId, 'Photo Studio remote controls\nActions requiring sensors must be approved on the device. Commands are available only while the Remote Control screen is open.', { reply_markup: menu });
}
function validDevice(req) {
  const auth = String(req.headers.authorization || '');
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
  const d = [...devices.values()].find(x => x.clientToken === token && token.length > 20);
  return d || null;
}
function enqueue(device, chatId, action) {
  const id = random(8);
  const q = deviceCommands.get(device.deviceId) || [];
  q.push({ id, action, createdAt: now() });
  deviceCommands.set(device.deviceId, q);
  commandIndex.set(id, { chatId, deviceId: device.deviceId, action, expiresAt: now() + 120_000 });
  bot.sendMessage(chatId, `Request sent: ${action}. If the app is connected, confirm or deny it on the device.`).catch(() => {});
}
app.get('/api/health', (_req, res) => res.json({ ok: true, service: 'Photo Studio control bridge', version: '3.0.0' }));
app.post('/api/register', (req, res) => {
  // Registration creates a short-lived pairing code and a random per-device bearer token.
  const deviceId = random(8);
  const pairCode = String(100000 + crypto.randomInt(900000));
  const clientToken = random(32);
  const device = { deviceId, clientToken, createdAt: now(), pairedChatId: null, lastSeen: null };
  devices.set(deviceId, device);
  pairCodes.set(pairCode, { deviceId, expiresAt: now() + 10 * 60_000 });
  res.status(201).json({ deviceId, clientToken, pairCode, expiresInSeconds: 600 });
});
app.get('/api/poll', (req, res) => {
  const device = validDevice(req);
  if (!device || req.query.deviceId !== device.deviceId) return res.status(401).json({ error: 'UNAUTHORIZED' });
  device.lastSeen = now();
  const q = deviceCommands.get(device.deviceId) || [];
  deviceCommands.set(device.deviceId, []);
  res.json({ commands: q.filter(c => now() - c.createdAt < 120_000).map(({ id, action }) => ({ id, action })) });
});
app.post('/api/ack', (req, res) => {
  const device = validDevice(req);
  if (!device) return res.status(401).json({ error: 'UNAUTHORIZED' });
  const { id, ok, message } = req.body || {};
  const item = commandIndex.get(String(id || ''));
  if (!item || item.deviceId !== device.deviceId || item.expiresAt < now()) return res.status(404).json({ error: 'COMMAND_NOT_FOUND' });
  commandIndex.delete(String(id));
  const safe = String(message || '').slice(0, 500);
  bot.sendMessage(item.chatId, `${item.action}: ${ok ? 'APPROVED / COMPLETED' : 'DENIED / FAILED'}\n${safe}`).catch(() => {});
  res.json({ ok: true });
});

bot.onText(/^\/(start|menu)(?:@\w+)?$/, async (msg) => {
  if (!admin(msg)) return bot.sendMessage(msg.chat.id, 'Access denied.');
  if (msg.chat.type !== 'private') return bot.sendMessage(msg.chat.id, 'Use this bot in a private chat.');
  await bot.sendMessage(msg.chat.id, 'Pair your device first: open Photo Studio → Remote → Register / Connect, then send /pair CODE here.');
  if (pairedChats.has(String(msg.chat.id))) await showMenu(msg.chat.id);
});
bot.onText(/^\/pair\s+(\d{6})$/, async (msg, match) => {
  if (!admin(msg) || msg.chat.type !== 'private') return bot.sendMessage(msg.chat.id, 'Pairing denied. Use an authorized private chat.');
  const code = match[1], entry = pairCodes.get(code);
  if (!entry || entry.expiresAt < now()) return bot.sendMessage(msg.chat.id, 'Pair code invalid or expired. Register again in the app.');
  const device = devices.get(entry.deviceId);
  if (!device) return bot.sendMessage(msg.chat.id, 'Device registration no longer exists.');
  // One admin chat per device; a new pairing replaces the old pairing.
  device.pairedChatId = String(msg.chat.id);
  pairedChats.set(String(msg.chat.id), device.deviceId);
  pairCodes.delete(code);
  await bot.sendMessage(msg.chat.id, 'Device paired successfully. Open the app and keep Remote Control visible while testing.');
  await showMenu(msg.chat.id);
});
bot.on('callback_query', async q => {
  try {
    if (!q.message || !admins.has(String(q.from?.id || '')) || q.message.chat.type !== 'private') {
      await bot.answerCallbackQuery(q.id, { text: 'Access denied' }); return;
    }
    const deviceId = pairedChats.get(String(q.message.chat.id));
    const device = devices.get(deviceId);
    if (!device || device.pairedChatId !== String(q.message.chat.id)) {
      await bot.answerCallbackQuery(q.id, { text: 'Pair a device first' }); return;
    }
    const action = q.data;
    if (!['status', 'camera', 'camera_front', 'camera_rear', 'flash_on', 'flash_off'].includes(action)) {
      await bot.answerCallbackQuery(q.id, { text: 'Unknown action' }); return;
    }
    await bot.answerCallbackQuery(q.id, { text: 'Request queued' });
    enqueue(device, String(q.message.chat.id), action);
  } catch (e) { try { await bot.answerCallbackQuery(q.id, { text: 'Request failed' }); } catch (_) {} }
});
bot.on('polling_error', e => console.error('Telegram polling error:', e.message));
const server = app.listen(Number(PORT), '0.0.0.0', () => console.log(`Photo Studio bridge listening on ${PORT}`));
function cleanup() {
  const t = now();
  for (const [code, p] of pairCodes) if (p.expiresAt < t) pairCodes.delete(code);
  for (const [id, c] of commandIndex) if (c.expiresAt < t) commandIndex.delete(id);
  for (const [id, d] of devices) if (t - d.createdAt > 30 * 24 * 60 * 60_000 && !d.lastSeen) devices.delete(id);
}
const cleanupTimer = setInterval(cleanup, 60_000);
function shutdown() { clearInterval(cleanupTimer); bot.stopPolling().catch(() => {}); server.close(() => process.exit(0)); }
process.on('SIGINT', shutdown); process.on('SIGTERM', shutdown);
