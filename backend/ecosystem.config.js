// Cấu hình PM2 - quản lý tiến trình Node.js trên VPS
//   pm2 startOrReload ecosystem.config.js --env production
module.exports = {
  apps: [
    {
      name: 'dating-api',
      script: 'src/server.js',
      cwd: __dirname,
      instances: 1, // giữ 1 instance: WebSocket sau này cần state chung
      exec_mode: 'fork',
      max_memory_restart: '400M',
      time: true, // thêm thời gian vào log
      env_production: {
        NODE_ENV: 'production',
      },
    },
  ],
};
