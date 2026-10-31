const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');

const env = require('./config/env');
const db = require('./config/db');
const { UPLOAD_DIR } = require('./middlewares/upload');
const { notFound, errorHandler } = require('./middlewares/errorHandler');
const { ok } = require('./utils/response');

const authRoutes = require('./modules/auth/auth.routes');
const userRoutes = require('./modules/users/users.routes');
const homeRoutes = require('./modules/home/home.routes');

const app = express();

app.set('trust proxy', 1); // chạy sau Nginx trên VPS
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(cors({ origin: env.corsOrigins.includes('*') ? true : env.corsOrigins }));
app.use(express.json({ limit: '1mb' }));
app.use(morgan(env.isProduction ? 'combined' : 'dev'));

// Ảnh người dùng upload: GET /uploads/<file>
app.use('/uploads', express.static(UPLOAD_DIR, { maxAge: '7d' }));

// Kiểm tra server + DB còn sống (dùng cho script deploy / monitor)
app.get('/api/health', async (req, res) => {
  let database = 'up';
  try {
    await db.query('SELECT 1');
  } catch {
    database = 'down';
  }
  ok(res, { status: 'ok', database, uptime: Math.round(process.uptime()) }, 'OK', database === 'up' ? 200 : 503);
});

app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/home', homeRoutes);

app.use(notFound);
app.use(errorHandler);

module.exports = app;
