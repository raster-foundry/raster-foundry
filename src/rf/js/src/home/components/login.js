'use strict';

var React = require('react'),
    _ = require('underscore'),
    router = require('../../router').router,
    asset = require('../../core/utils').asset,
    settings = require('../../settings');

var Login = React.createBackboneClass({
    handleLogin: function(e) {
        e.preventDefault();

        var loginPromise = settings.getUser().login({
            'username': this.state.username,
            'password': this.state.password
        });

        loginPromise
            .done(_.bind(this.handleSuccess, this))
            .fail(_.bind(this.handleFailure, this));
    },

    handleSuccess: function() {
        this.setState({invalidCredentials: false});
        router.go('/');
    },

    handleFailure: function() {
        this.setState({invalidCredentials: true});
    },

    getInitialState: function() {
        return {
            invalidCredentials: false,
            username: '',
            password: ''
        };
    },

    updateUsername: function(event) {
        this.setState({username: event.target.value});
    },

    updatePassword: function(event) {
        this.setState({password: event.target.value});
    },

    render: function() {
        var errorMessage = this.state.invalidCredentials ?
            'Invalid username and/or password' : '';

        return (
            <div>
                <div className="block login-block">
                    <form method="post" onSubmit={this.handleLogin}>
                        <img src={asset('img/logo-raster-foundry.png')} />
                        <div className="login-error">
                            {errorMessage}
                        </div>
                        <div className="form-group">
                            <label htmlFor="login-username">Username</label>
                            <input type="text"
                                className="form-control"
                                id="login-username"
                                value={this.state.username}
                                onChange={this.updateUsername} />
                        </div>
                        <div className="form-group">
                            <label htmlFor="login-password">Password</label>
                            <input type="password"
                                className="form-control"
                                id="login-password"
                                value={this.state.password}
                                onChange={this.updatePassword} />
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
