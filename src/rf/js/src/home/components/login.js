'use strict';

var React = require('react'),
    asset = require('../../core/utils').asset;

var Login = React.createBackboneClass({
    render: function() {
        return <div>
            <div className="block login-block">
                <img src={asset('img/logo-raster-foundry.png')} />
                <div className="form-group">
                    <label htmlFor="search">Username</label>
                    <input type="text" className="form-control" id="search" />
                </div>
                <div className="form-group">
                    <label htmlFor="login-password">Password</label>
                    <input type="password" className="form-control" id="login-password" />
                </div>
                <div className="form-action">
                    <a href="#" className="btn btn-secondary btn-block"
                        onClick={this.props.handleLogin}>Log In</a>
                    <a href="#" className="text-muted small">Forgot Password?</a>
                </div>
            </div>

            <div className="video-background">
                <img src={asset('img/ffc-space.png')} className="placeholder" />
                <video autoPlay loop preload>
                    <source src={asset('video/ffc-space.webm')} type="video/webm" />
                    <source src={asset('video/ffc-space.m4v')} type="video/mp4" />
                    <source src={asset('video/ffc-space.ogv')} type="video/ogg" />
                </video>
            </div>
        </div>;
    }
});

module.exports = Login;
