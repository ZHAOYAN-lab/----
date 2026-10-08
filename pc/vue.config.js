const path = require('path');
const original = path.resolve(__dirname, '../AOABLUETOOTH/xiyou_web_code');
module.exports = {
  publicPath: '/console/',
  outputDir: path.join(__dirname, 'dist'),
  productionSourceMap: false,
  lintOnSave: false,
  chainWebpack(config) { config.plugins.delete('copy'); },
  pages: { index: { entry: path.join(__dirname, 'vue-main.js'), template: path.join(__dirname, 'template.html'), title: '仓库定位与发光寻找' } },
  configureWebpack: { resolve: { modules: [path.join(original, 'node_modules'), 'node_modules'], alias: { '@': path.join(original, 'src'), '~theme': path.join(original, 'theme') } } },
  css: { loaderOptions: { less: { lessOptions: {
    javascriptEnabled: true, math: 'always', rewriteUrls: 'off',
    plugins: [{ install(less, manager) { manager.addPreProcessor({ process(source) { return source.replace(/url\((['"])@\//g, 'url($1~@/'); } }); } }]
  } } } }
};
