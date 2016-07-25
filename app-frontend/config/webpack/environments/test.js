'use strict';
module.exports = function(_path) {
    return {
        context: _path,
        debug: true,
        devtool: 'cheap-source-map',
        entry: {},
        module: {
            preLoaders: [{
                test: /\.js$/,
                exclude: [
                        /node_modules/,
                        /\.spec\.js$/
                ],
                loader: 'isparta-instrumenter'
            }],
            loaders: [
                {
                    test: /\.js$/,
                    loaders: ['babel'],
                    exclude: /node_modules/
                },
                {
                    test: /\.scss$/,
                    loader: 'null'
                }
            ]
        }
    };
};
