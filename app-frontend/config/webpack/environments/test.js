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
                    test: /\.scss$/,
                    loader: 'null'
                }, {
                    test: /\.html$/,
                    loader: 'null'
                }
            ]
        }
    };
};
