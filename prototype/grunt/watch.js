module.exports = {
  script: {
		files: ['app/**/*.js'],
		options: {
			spawn: false,
		}
	},

	css: {
    files: ['app/**/*.scss'],
    tasks: ['sass', 'postcss'],
    options: {
      spawn: false,
    }
	}
};