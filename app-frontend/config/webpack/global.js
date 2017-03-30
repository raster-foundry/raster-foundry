'use strict';
/* globals process module */
/* eslint no-process-env: 0
 no-console: 0
 */

// Depends
const path = require('path');
const webpack = require('webpack');
const autoprefixer = require('autoprefixer-core');
const Manifest = require('manifest-revision-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const NODE_ENV = process.env.NODE_ENV || 'production';
const DEVELOPMENT = NODE_ENV === 'production' ? false : true;
const stylesLoader = 'css-loader?sourceMap!postcss-loader!sass-loader?' +
        'outputStyle=expanded&sourceMap=true&sourceMapContents=true';

module.exports = function (_path) {
    let rootAssetPath = _path + 'src';

    let webpackConfig = {
        // entry points
        entry: {
            vendor: _path + '/src/app/index.vendor.js',
            app: _path + '/src/app/index.bootstrap.js',
            polyfill: _path + '/node_modules/babel-polyfill'
        },

        // output system
        output: {
            path: 'dist',
            filename: '[name].js',
            publicPath: '/'
        },

        // resolves modules
        resolve: {
            extensions: ['', '.js'],
            modulesDirectories: ['node_modules'],
            alias: {
                _appRoot: path.join(_path, 'src', 'app'),
                _images: path.join(_path, 'src', 'app', 'assets', 'images'),
                _stylesheets: path.join(_path, 'src', 'app', 'assets', 'styles'),
                _scripts: path.join(_path, 'src', 'app', 'assets', 'js')
            }
        },

        // modules resolvers
        module: {
            noParse: [],
            preLoaders: [
                {
                    test: /\.js$/,
                    loaders: ['eslint-loader'],
                    exclude: [/node_modules/, /tests\.webpack\.js/, /\.config.js/, /\.spec\.js$/]
                }
            ],
            loaders: [{
                test: /\.html$/,
                loaders: [
                    'ngtemplate-loader?relativeTo=' + _path,
                    'html-loader?attrs[]=img:src&attrs[]=img:data-src&attrs[]=source:src'
                ]
            }, {
                test: /\.js$/,
                loaders: [
                    'baggage-loader?[file].html&[file].css'
                ]
            }, {
                test: /\.js$/,
                exclude: [
                    path.resolve(_path, 'node_modules')
                ],
                loaders: [
                    'ng-annotate-loader'
                ]
            }, {
                test: /\.js$/,
                exclude: [
                    path.resolve(_path, 'node_modules')
                ],
                loader: 'babel-loader',
                query: {
                    cacheDirectory: true,
                    plugins: ['transform-runtime', 'add-module-exports'],
                    presets: ['angular', 'latest']
                }
            }, {
                test: /\.css$/,
                loader: DEVELOPMENT ? 'style-loader!css-loader?sourceMap!postcss-loader'
                    : ExtractTextPlugin.extract('style-loader',
                                                'css-loader!postcss-loader')
            }, {
                test: /\.(scss|sass)$/,
                loader: DEVELOPMENT ? 'style-loader!' + stylesLoader
                    : ExtractTextPlugin.extract('style-loader', stylesLoader)
            }, {
                test: /\.(woff2|woff|ttf|eot|svg)?(\?v=[0-9]\.[0-9]\.[0-9])?$/,
                loaders: [
                    'url-loader?name=assets/fonts/[name]_[hash].[ext]'
                ]
            }, {
                test: /\.(jpe?g|png|gif)$/i,
                loaders: [
                    'url-loader?name=assets/images/[name]_[hash].[ext]&limit=10000!image-webpack'
                ]
            }, {
                test: /\.(m4v|ogg|webm)$/i,
                loaders: [
                    'url-loader?name=assets/video/[name]_[hash].[ext]&limit=10000'
                ]
            }, {
                test: require.resolve('angular-deferred-bootstrap'),
                loaders: [
                    'expose?deferredBootstrapper'
                ]
            }, {
                test: require.resolve('angular'),
                loaders: [
                    'expose?angular'
                ]
            }, {
                test: require.resolve('jquery'),
                loaders: [
                    'expose?$',
                    'expose?jQuery'
                ]
            }, {
                test: require.resolve('leaflet'),
                loaders: [
                    'expose?L'
                ]
            }, {
                test: require.resolve('jointjs'),
                loaders: [
                    'expose?joint'
                ]
            }, {
                test: require.resolve('moment'),
                loaders: [
                    'expose?moment'
                ]
            }, {
                test: /node_modules[\\\/]auth0-lock[\\\/].*\.js$/,
                loaders: ['transform-loader/cacheable?brfs',
                          'transform-loader/cacheable?packageify']
            }, {
                test: /node_modules[\\\/]auth0-lock[\\\/].*\.ejs$/,
                loader: 'transform-loader/cacheable?ejsify'
            }, {
                test: /\.json$/,
                loader: 'json'
            }]
        },

        // post css
        postcss: [autoprefixer({browsers: ['last 5 versions']})],

        imageWebpackLoader: {
            pngquant: {
                quality: '66-90',
                speed: 4
            }
        },

        eslint: {
            configFile: './.eslintrc'
        },

        // load plugins
        plugins: [
            new webpack.ProvidePlugin({
                $: 'jquery',
                jQuery: 'jquery',
                L: 'leaflet'
            }),
            new webpack.IgnorePlugin(/^\.\/locale$/, /moment$/),
            new webpack.optimize.DedupePlugin(),
            new webpack.optimize.AggressiveMergingPlugin({
                moveToParents: true
            }),
            new webpack.optimize.CommonsChunkPlugin({
                name: 'common',
                async: true,
                children: true,
                minChunks: Infinity
            }),
            new Manifest(path.join(_path + '/config', 'manifest.json'), {
                rootAssetPath: rootAssetPath,
                ignorePaths: ['.DS_Store']
            }),
            new ExtractTextPlugin(
                'assets/styles/css/[name]' +
                    (NODE_ENV === 'development' ? '' : '.[chunkhash]') +
                    '.css', {allChunks: true}
            ),
            new HtmlWebpackPlugin({
                filename: 'index.html',
                template: path.join(_path, 'src', 'tpl-index.html'),
                heapLoad: DEVELOPMENT ? '2743344218' : '3505855839'
            })
        ]
    };

    return webpackConfig;
};
