'use strict';

var React = require('react'),
    asset = require('../../core/utils').asset;

var Login = React.createBackboneClass({
    render: function() {
        return (
            <div>
                <div className="block login-block">
                    <form method="post" onSubmit={this.props.handleLogin}>
                        <img src={asset('img/logo-raster-foundry.png')} />
                        <div className="form-group">
                            <label htmlFor="login-username">Username</label>
                            <input type="text" className="form-control" id="login-username" />
                        </div>
                        <div className="form-group">
                            <label htmlFor="login-password">Password</label>
                            <input type="password" className="form-control" id="login-password" />
                        </div>
                        <div className="form-action">
                            <input type="submit" className="btn btn-secondary btn-block" value="Log In" />
                            <a href="#" className="text-muted small">Forgot Password?</a>
                        </div>
                    </form>
                </div>

                <div className="video-background">
                    <img src={asset('img/ffc-space.png')} className="placeholder" />
                    <video autoPlay loop preload>
                        <source src={asset('video/ffc-space.webm')} type="video/webm" />
                        <source src={asset('video/ffc-space.m4v')} type="video/mp4" />
                        <source src={asset('video/ffc-space.ogv')} type="video/ogg" />
                    </video>
                </div>
            </div>
        );
    }
});

module.exports = Login;
