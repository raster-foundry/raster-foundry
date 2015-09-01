'use strict';

var React = require('react'),
    _ = require('underscore'),
    router = require('../router').router,
    asset = require('../core/utils').asset,
    settings = require('../settings');

var VideoBackground = React.createBackboneClass({
    render: function() {
        return (
            <div className="video-background">
                <img src={asset('img/ffc-space.png')} className="placeholder" />
                <video autoPlay loop preload>
                    <source src={asset('video/ffc-space.webm')} type="video/webm" />
                    <source src={asset('video/ffc-space.m4v')} type="video/mp4" />
                    <source src={asset('video/ffc-space.ogv')} type="video/ogg" />
                </video>
            </div>
        );
    }
});

var UserScreen = React.createBackboneClass({
    render: function() {
        return (
            <div>
                <div className="block user-screen-block">
                    <img src={asset('img/logo-raster-foundry.png')} />
                    {this.props.content}
                </div>
                <VideoBackground />
            </div>
        );
    }
});

var ModelValidationMixin = {
    handleServerSuccess: function() {
        this.getModel().set({
            'success': true,
            'client_errors': null,
            'server_errors': null
        });
    },

    // Default failure handler, will display any `errors` received from server.
    handleServerFailure: function(response) {
        var server_errors = ['Server communication error'];
        if (response && response.responseJSON && response.responseJSON.errors) {
            server_errors = response.responseJSON.errors;
        }
        this.getModel().set({
            'success': false,
            'client_errors': null,
            'server_errors': server_errors
        });
    },

    // Performs Primary Action if model is in valid state.
    validate: function(e) {
        e.preventDefault();
        this.setFields();
        if (this.getModel().isValid()) {
            this.primaryAction();
        } else {
            this.onValidateFailure();
        }
    },

    renderErrors: function() {
        var self = this;
        function renderErrorItems(errorKey) {
            var errors = '';
            if (self.getModel().get(errorKey)) {
                errors = self.getModel().get(errorKey).map(function(error,i) {
                    return <li key={errorKey + '-' + i}>{error}</li>;
                });
            }
            return errors;
        }

        return (
            <ul>
                {renderErrorItems('client_errors')}
                {renderErrorItems('server_errors')}
            </ul>
        );
    }
};

var InputField = React.createBackboneClass({
    getDefaultProps: function() {
        return {
            type: 'text'
        };
    },

    getValue: function() {
        if (this.props.type === 'checkbox') {
            return React.findDOMNode(this.refs.input).checked;
        } else {
            return React.findDOMNode(this.refs.input).value;
        }
    },

    setFocus: function() {
        React.findDOMNode(this.refs.input).focus();
    },

    render: function() {
        if (this.props.type === 'checkbox') {
            return (
                <div className="checkbox form-group">
                    <input type="checkbox"
                        ref="input"
                        id={this.props.name} />
                    <label>
                        {this.props.displayName}
                    </label>
                </div>
            );
        } else {
            return (
                <div className="form-group">
                    <label htmlFor={this.props.name}>{this.props.displayName}</label>
                    <input type={this.props.type}
                        className="form-control"
                        ref="input"
                        id={this.props.name}
                        defaultChecked={this.props.value} />
                </div>
            );
        }
    }
});

var LoginScreen = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    onValidateFailure: function() {
        this.refs.username.setFocus();
    },

    setFields: function() {
        this.getModel().set({
            username: this.refs.username.getValue(),
            password: this.refs.password.getValue()
        }, { silent: true });
    },

    primaryAction: function() {
        settings.getUser().login(this.getModel().attributes)
            .done(_.bind(this.handleSuccess, this))
            .fail(_.bind(this.handleFailure, this));
    },

    handleSuccess: function() {
        router.go('/');
    },

    handleFailure: function(response) {
        this.handleServerFailure(response);
    },

    render: function() {
        var content = (
            <div>
                {this.renderErrors()}
                <div className="link-block">
                    <a href="/forgot/" data-url="/forgot/"
                        className="text-muted small forgot">Forgot?</a>
                    <a href="/send-activation/" data-url="/send-activation/"
                        className="text-muted small send-activation">Send Activation Email</a>
                    <a href="/sign-up/" data-url="/sign-up/"
                        className="text-muted small sign-up">Sign Up</a>
                </div>
                <form method="post" onSubmit={this.validate} >
                    <InputField name="username" displayName="Username"
                        ref="username"
                        value={this.getModel().get('username')}/>
                    <InputField name="password" displayName="Password"
                        ref="password" type="password"
                        value={this.getModel().get('password')}/>
                    <div className="form-action">
                        <input type="submit" className="btn btn-secondary btn-block" value="Log In" />
                    </div>
                </form>
            </div>
        );

        return <UserScreen content={content} />;
    }
});

var SignUpScreen = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    onValidateFailure: function() {
        this.refs.username.setFocus();
    },

    getFormData: function() {
        return {
            username: this.refs.username.getValue(),
            email: this.refs.email.getValue(),
            password1: this.refs.password1.getValue(),
            password2: this.refs.password2.getValue(),
            agreed: this.refs.agreed.getValue()
        };
    },

    setFields: function() {
        this.getModel().set(this.getFormData());
    },

    primaryAction: function() {
        this.getModel()
            .fetch({
                method: 'POST',
                data: this.getFormData()
            })
            .done(_.bind(this.handleServerSuccess, this))
            .fail(_.bind(this.handleServerFailure, this));
    },

    handleSuccess: function() {
    },

    handleFailure: function(response) {
        this.handleServerFailure(response);
    },

    render: function() {
        var content = (
            <div>
                <div className="user-message">
                    Please check your email to activate your account.
                </div>
                <a href="#" data-url="/login" className="btn btn-secondary btn-block">Login</a>
            </div>
        );
        if (!this.getModel().get('success')) {
            content = (
                <form noValidate method="post" onSubmit={this.validate}>
                    {this.renderErrors()}
                    <InputField name="username" displayName="Username" ref="username" value={this.getModel().get('username')}/>
                    <InputField name="email" displayName="Email" ref="email" type="email" value={this.getModel().get('email')}/>
                    <InputField name="password1" displayName="Password" type="password" ref="password1" value={this.getModel().get('password1')}/>
                    <InputField name="password2" displayName="Re-enter Password" type="password" ref="password2" value={this.getModel().get('password2')}/>
                    <InputField name="agreed" displayName="I agree to something" type="checkbox" ref="agreed" value={this.getModel().get('agreed')}/>
                    <div className="form-action">
                        <input type="submit" className="btn btn-secondary btn-block" value="Sign Up" />
                    </div>
                </form>
            );
        }

        return <UserScreen content={content} />;
    }
});

var SendActivationScreen = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    onValidateFailure: function() {
        this.refs.email.setFocus();
    },

    getFormData: function() {
        return {
            email: this.refs.email.getValue()
        };
    },

    setFields: function() {
        this.getModel().set(this.getFormData());
    },

    primaryAction: function() {
        this.getModel()
            .fetch({
                method: 'POST',
                data: this.getFormData()
            })
            .done(_.bind(this.handleServerSuccess, this))
            .fail(_.bind(this.handleServerFailure, this));
    },

    handleSuccess: function() {
    },

    handleFailure: function(response) {
        this.handleServerFailure(response);
    },

    render: function() {
        var content = (
            <div>
                <div className="user-message">
                    Please check your email to activate your account.
                </div>
                <a href="#" data-url="/login" className="btn btn-secondary btn-block">Login</a>
            </div>
        );
        if (!this.getModel().get('success')) {
            content = (
                <form method="post" onSubmit={this.validate} noValidate >
                    {this.renderErrors()}
                    <InputField name="email" displayName="Email" type="email" ref="email" value={this.getModel().get('email')}/>
                    <div className="form-action">
                        <input type="submit" className="btn btn-secondary btn-block" value="Send Activation Email" />
                    </div>
                </form>
            );
        }

        return <UserScreen content={content} />;
    }
});

var ForgotScreen = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    onValidateFailure: function() {
        this.refs.email.setFocus();
    },

    getFormData: function() {
        return {
            email: this.refs.email.getValue()
        };
    },

    setFields: function() {
        this.getModel().set(this.getFormData());
    },

    primaryAction: function() {
        this.getModel()
            .fetch({
                method: 'POST',
                data: this.getFormData()
            })
            .done(_.bind(this.handleServerSuccess, this))
            .fail(_.bind(this.handleServerFailure, this));
    },

    handleSuccess: function() {
    },

    handleFailure: function(response) {
        this.handleServerFailure(response);
    },

    render: function() {
        var content = 'Please check your email to reset your password.';
        if (!this.getModel().get('success')) {
            content = (
                <form method="post" onSubmit={this.validate} noValidate>
                    {this.renderErrors()}
                    <InputField name="email" displayName="Email" ref="email"
                        type="email" value={this.getModel().get('email')}/>
                    <div className="form-action">
                        <input type="submit"
                            className="btn btn-secondary btn-block"
                            value="Retrieve" />
                    </div>
                </form>
            );
        }

        return <UserScreen content={content} />;
    }
});

var ActivateScreen = React.createBackboneClass({
    render: function() {
        var content = (
            <div>
                <div className="user-message">
                    Activation is complete and you are now logged in.
                </div>
                <a href="#" data-url="/" className="btn btn-secondary btn-block">Proceed To Library</a>
            </div>
        );

        return <UserScreen content={content} />;
    }
});

module.exports = {
    LoginScreen: LoginScreen,
    SignUpScreen: SignUpScreen,
    SendActivationScreen: SendActivationScreen,
    ForgotScreen: ForgotScreen,
    ActivateScreen: ActivateScreen
};
