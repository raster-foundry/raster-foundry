module.exports = {
  options: {
    map: false,
    processors: [
      require('autoprefixer')({browsers: ['last 2 versions']}),
      require('cssnano')({
        'safe': true
      }) // minify the result
    ],
  },
  dist: {
    src: 'app/assets/css/*.css'
  }
};
