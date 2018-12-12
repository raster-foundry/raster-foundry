'use strict';
/* globals process module __dirname */
/* eslint no-process-env: 0
 no-console: 0
 */

// Depends
const path = require('path');
const webpack = require('webpack');
const autoprefixer = require('autoprefixer-core');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const postcssPresetEnv = require('postcss-preset-env');
const CopyWebpackPlugin = require('copy-webpack-plugin');

const NODE_ENV = process.env.NODE_ENV || 'production';
const DEVELOPMENT = NODE_ENV === 'production' ? false : true;

const HERE_APP_ID = 'v88MqS5fQgxuHyIWJYX7';
const HERE_APP_CODE = '5pn07ENomTHOap0u7nQSFA';

const INTERCOM_APP_ID = '';
const GOOGLE_TAG_ID = 'GTM-54XHDBP';

const basemaps = JSON.stringify({
    layers: {
        Light: {
            url: 'https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png',
            properties: {
                attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">' +
                    'OpenStreetMap</a> &copy;<a href="http://cartodb.com/attributions">CartoDB</a>',
                maxZoom: 30
            },

            default: true
        },
        Dark: {
            url: 'https://cartodb-basemaps-{s}.global.ssl.fastly.net/dark_all/{z}/{x}/{y}.png',
            properties: {
                attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">' +
                    'OpenStreetMap</a> &copy;<a href="http://cartodb.com/attributions">CartoDB</a>',
                maxZoom: 30,
                subdomains: 'a'
            }
        },
        Aerial: {
            url: 'https://{s}.{base}.maps.cit.api.here.com/maptile/2.1/{type}/{mapID}/hybrid.day' +
                '/{z}/{x}/{y}/{size}/{format}?app_id={app_id}&app_code={app_code}&lg={language}',
            properties: {
                attribution: 'Map &copy; 1987-2014 <a href="http://developer.here.com">HERE</a>',
                subdomains: '1234',
                mapID: 'newest',
                'app_id': HERE_APP_ID,
                'app_code': HERE_APP_CODE,
                base: 'aerial',
                maxZoom: 30,
                maxNativeZoom: 20,
                type: 'maptile',
                language: 'eng',
                format: 'png8',
                size: '256'
            }
        },
        Streets: {
            url: 'https://{s}.base.maps.cit.api.here.com/maptile/2.1/maptile/newest/normal.day/' +
                '{z}/{x}/{y}/256/png8?app_id={app_id}&app_code={app_code}',
            properties: {
                attribution: 'Map &copy; 1987-2014 <a href="http://developer.here.com">HERE</a>',
                subdomains: '1234',
                'app_id': HERE_APP_ID,
                'app_code': HERE_APP_CODE
            }
        }
    },
    default: 'Light'
});

module.exports = function (_path) {
    let rootAssetPath = _path + 'src';

    let webpackConfig = {
        // entry points
        entry: {
            // vendor: _path + '/src/app/index.vendor.js',
            app: _path + '/src/app/index.bootstrap.js'
            // wasm: _path + '/node_modules/gdal-js/gdal.wasm'
            // polyfill: _path + '/node_modules/babel-polyfill'
        },

        // output system
        output: {
            path: path.resolve(_path, 'dist'),
            filename: '[name].[chunkhash].js',
            publicPath: '/'
        },
        target: 'web',
        optimization: {
            splitChunks: {
                cacheGroups: {
                    commons: {
                        test: /[\\/]node_modules[\\/]/,
                        name: 'vendor',
                        chunks: 'initial'
                    }
                }
            }
        },

        // resolves modules
        resolve: {
            extensions: ['.js'],
            modules: ['node_modules'],
            alias: {
                _appRoot: path.join(_path, 'src', 'app'),
                _stylesheets: path.join(_path, 'src', 'assets', 'styles'),
                _api: path.join(_path, 'src', 'app', 'api'),
                _redux: path.join(_path, 'src', 'app', 'redux'),
                _assets: path.join(_path, 'src', 'assets'),
                _scripts: path.join(_path, 'src', 'assets', 'js'),
                _images: path.join(_path, 'src', 'assets', 'images'),
                _font: path.join(_path, 'src', 'assets', 'font'),
                loamLib: path.join(_path, 'node_modules', 'loam', 'lib'),
                gdalJs: path.join(_path, 'node_modules', 'gdal-js'),
                moment: 'moment/moment.js'
            },
            mainFields: ['module', 'jsnext:main', 'main']
        },

        // modules resolvers
        module: {
            exprContextRegExp: /^\.\/*$/,
            unknownContextRegExp: /^\.\/.*$/,
            rules: [{
                enforce: 'pre',
                test: /\.js$/,
                exclude: [/node_modules/, /tests\.webpack\.js/, /\.config.js/, /\.spec\.js$/],
                loader: 'eslint-loader',
                options: {
                    fix: true
                }
            }, {
                test: /\.html$/,
                exclude: [
                    path.resolve(_path, 'src/tpl-index.html')
                ],
                loaders: [
                    'ngtemplate-loader?relativeTo=' + _path,
                    'html-loader?attrs[]=img:src&attrs[]=img:data-src&attrs[]=source:src'
                ]
            }, {
                test: /\.js$/,
                exclude: [
                    path.resolve(_path, 'node_modules')
                ],
                loaders: [
                    'ng-annotate-loader?ngAnnotate=ng-annotate-patched'
                ]
            }, {
                test: /\.js$/,
                exclude: [
                    path.resolve(_path, 'node_modules'),
                    path.resolve(_path, 'src/app/services/vendor/aws-sdk-s3.module.js')
                ],
                loader: 'babel-loader'
            }, {
                test: /\.css$/,
                use: [
                    'style-loader',
                    {
                        loader: 'css-loader',
                        options: {
                            sourceMap: Boolean(DEVELOPMENT),
                            importLoaders: 1
                        }
                    },
                    {
                        loader: 'postcss-loader',
                        options: {
                            ident: 'postcss',
                            plugins: () => [
                                postcssPresetEnv()
                            ]
                        }
                    }
                ]
            }, {
                test: /\.(scss|sass)$/,
                use: [
                    'style-loader',
                    {
                        loader: 'css-loader',
                        options: {
                            sourceMap: Boolean(DEVELOPMENT),
                            importLoaders: 1
                        }
                    },
                    {
                        loader: 'postcss-loader',
                        options: {
                            ident: 'postcss',
                            plugins: () => [
                                postcssPresetEnv()
                            ]
                        }
                    },
                    {
                        loader: 'sass-loader',
                        options: {
                            outputStyle: 'expanded',
                            sourceMap: true,
                            sourceMapContents: true
                        }
                    }
                ]
            }, {
                test: /\.(woff2|woff|ttf|eot)(\?[a-z0-9]+)?$/,
                loaders: [
                    'url-loader?name=assets/fonts/[name]_[hash].[ext]'
                ]
            }, {
                test: /\.(svg)$/i,
                use: [
                    {
                        loader: 'url-loader',
                        options: {
                            name: 'assets/images/[name]_[hash].[ext]',
                            limit: 8192
                        }
                    }
                ]
            }, {
                test: /\.(jpe?g|png|gif)$/i,
                use: [
                    {
                        loader: 'url-loader',
                        options: {
                            name: 'assets/images/[name]_[hash].[ext]',
                            limit: 8192
                            // fallback: 'responsive-loader' // TODO: Check if this would help
                        }
                    }
                ]
            }, {
                test: /(loam-worker\.js|gdal\.js|gdal\.wasm|gdal\.data)$/,
                type: 'javascript/auto',
                loader: 'file-loader?name=[name].[ext]'
            }, {
                test: require.resolve('angular-deferred-bootstrap'),
                loaders: [
                    'expose-loader?deferredBootstrapper'
                ]
            }, {
                test: require.resolve('jquery'),
                loaders: [
                    'expose-loader?$',
                    'expose-loader?jQuery'
                ]
            }, {
                test: require.resolve('leaflet'),
                loaders: [
                    'expose-loader?L'
                ]
            }, {
                test: require.resolve('moment'),
                loaders: [
                    'expose-loader?moment'
                ]
            }, {
                test: require.resolve('loam'),
                loaders: [
                    'expose-loader?loam'
                ]
            }]
        },
        // load plugins
        plugins: [
            new webpack.ProvidePlugin({
                $: 'jquery',
                jQuery: 'jquery',
                L: 'leaflet'
            }),
            new webpack.IgnorePlugin(/^\.\/locale$/, /moment$/),
            new webpack.optimize.AggressiveMergingPlugin({
                moveToParents: true
            }),
            new ExtractTextPlugin(
                'assets/styles/css/[name]' +
                    (NODE_ENV === 'development' ? '' : '.[chunkhash]') +
                    '.css', {allChunks: true}
            ),
            new HtmlWebpackPlugin({
                filename: 'index.html',
                template: path.join(_path, 'src', 'tpl-index.html'),
                heapLoad: DEVELOPMENT ? '2743344218' : '3505855839',
                gtagId: GOOGLE_TAG_ID,
                development: DEVELOPMENT,
                APP_NAME: 'Raster Foundry',
                inject: 'head'
            }),
            new CopyWebpackPlugin([{
                from: 'src/favicon',
                to: 'favicons'
            }]),
            new webpack.DefinePlugin({
                'BUILDCONFIG': {
                    APP_NAME: JSON.stringify('Raster Foundry'),
                    BASEMAPS: basemaps,
                    API_HOST: JSON.stringify(''),
                    HERE_APP_ID: JSON.stringify(HERE_APP_ID),
                    HERE_APP_CODE: JSON.stringify(HERE_APP_CODE),
                    INTERCOM_APP_ID: JSON.stringify(INTERCOM_APP_ID),
                    THEME: JSON.stringify('default'),
                    AUTH0_PRIMARY_COLOR: JSON.stringify('#465076'),
                    LOGOFILE: JSON.stringify('raster-foundry-logo.svg'),
                    LOGOURL: JSON.stringify(false),
                    FAVICON_DIR: JSON.stringify('/favicon'),
                    FEED_SOURCE: JSON.stringify(
                        'https://blog.rasterfoundry.com/latest?format=json'
                    ),
                    MAP_CENTER: JSON.stringify([-6.8, 39.2]),
                    MAP_ZOOM: 5
                },
                'HELPCONFIG': {
                    API_DOCS_URL: JSON.stringify('https://docs.rasterfoundry.com/'),
                    HELP_HOME: JSON.stringify('https://help.rasterfoundry.com/'),
                    GETTING_STARTED_WITH_PROJECTS: JSON.stringify(
                        'https://help.rasterfoundry.com/creating-projects'
                    ),
                    DEVELOPER_RESOURCES: JSON.stringify(
                        'https://help.rasterfoundry.com/developer-resources'
                    )
                }
            })
        ],
        node: {
            dgram: 'empty',
            fs: 'empty',
            net: 'empty',
            tls: 'empty'
        }
    };

    return webpackConfig;
};
