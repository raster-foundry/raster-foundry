module.exports = {
  dev: {
    bsFiles: {
      src : [
        'app/**/*.css',
        'app/**/*.html'
      ]
    },
    options: {
      watchTask: true,
      server: './app'
    }
  }
};