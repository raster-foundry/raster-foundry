'use strict';

var React = require('react'),
    _ = require('underscore'),
    router = require('../router').router,
    asset = require('../core/utils').asset,
    settings = require('../settings'),
    DropdownMenu = require('../home/components/menu').DropdownMenu,
    HeaderMenu = require('../home/components/menu').HeaderMenu;

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
        try {
            server_errors = response.responseJSON.errors.all;
        } catch (ex) {
            // Do nothing
        }
        this.getModel().set({
            'success': false,
            'client_errors': null,
            'server_errors': server_errors
        });
    },

    setFields: function() {
        this.getModel().set(this.getFormData());
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
            var errors = self.getModel().get(errorKey) || [];
            return errors.map(function(error, i) {
                return <li key={errorKey + '-' + i}>{error}</li>;
            });
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

var LoginBox = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    componentDidMount: function() {
        this.refs.username.setFocus();
    },

    onValidateFailure: function() {
        this.refs.username.setFocus();
    },

    getFormData: function() {
        return {
            username: this.refs.username.getValue(),
            password: this.refs.password.getValue()
        };
    },

    primaryAction: function() {
        settings.getUser().login(this.getModel().attributes)
            .done(_.bind(this.handleSuccess, this))
            .fail(_.bind(this.handleFailure, this));
    },

    handleSuccess: function() {
        router.go('/');
        settings.getPendingLayers().fetch();
    },

    handleFailure: function(response) {
        this.handleServerFailure(response);
    },

    render: function() {
        return (
            <div>
                {this.renderErrors()}
                <div className="link-block">
                    {/*<a href="/forgot/" data-url="/forgot/"
                        className="text-muted small forgot">Forgot?</a>*/}
                    {/*<a href="/send-activation/" data-url="/send-activation/"
                        className="text-muted small send-activation">Send Activation Email</a>*/}
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
    }
});

var SignUpBox = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    componentDidMount: function() {
        this.refs.username.setFocus();
    },

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

    primaryAction: function() {
        this.getModel()
            .fetch({
                method: 'POST',
                data: this.getFormData()
            })
            .done(_.bind(this.handleServerSuccess, this))
            .fail(_.bind(this.handleServerFailure, this));
    },

    render: function() {
        var content = '';
        if (this.getModel().get('success')) {
            content = (
                <div>
                    <div className="user-message">
                        {/*Please check your email to activate your account.*/}
                        Thank you for registering.
                    </div>
                    <a href="#" data-url="/login" className="btn btn-secondary btn-block">Login</a>
                </div>
            );
        } else {
            content = (
                <form method="post" onSubmit={this.validate} noValidate>
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
        return <div>{content}</div>;
    }
});

var SendActivationBox = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    componentDidMount: function() {
        this.refs.email.setFocus();
    },

    onValidateFailure: function() {
        this.refs.email.setFocus();
    },

    getFormData: function() {
        return {
            email: this.refs.email.getValue()
        };
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

    render: function() {
        var content = '';
        if (this.getModel().get('success')) {
            content = (
                <div>
                    <div className="user-message">
                        Please check your email to activate your account.
                    </div>
                    <a href="#" data-url="/login" className="btn btn-secondary btn-block">Login</a>
                </div>
            );
        } else {
            content = (
                <form method="post" onSubmit={this.validate} noValidate>
                    {this.renderErrors()}
                    <InputField name="email" displayName="Email" type="email" ref="email" value={this.getModel().get('email')}/>
                    <div className="form-action">
                        <input type="submit" className="btn btn-secondary btn-block" value="Send Activation Email" />
                    </div>
                </form>
            );
        }
        return <div>{content}</div>;
    }
});

var ForgotBox = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    componentDidMount: function() {
        this.refs.email.setFocus();
    },

    onValidateFailure: function() {
        this.refs.email.setFocus();
    },

    getFormData: function() {
        return {
            email: this.refs.email.getValue()
        };
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

    render: function() {
        var content = '';
        if (this.getModel().get('success')) {
            content = 'Please check your email to reset your password.';
        } else {
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
        return <div>{content}</div>;
    }
});

var ResetPasswordBox = React.createBackboneClass({
    mixins: [ModelValidationMixin],

    onValidateFailure: function() {
        this.refs.new_password1.setFocus();
    },

    getFormData: function() {
        return {
            new_password1: this.refs.new_password1.getValue(),
            new_password2: this.refs.new_password2.getValue(),
            uidb64: this.props.uidb64,
            token: this.props.token
        };
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

    render: function() {
        var content = '';
        if (this.getModel().get('success')) {
            content = (
                <div>
                    <div className="user-message">
                        Password reset was successful.
                    </div>
                    <a href="#" data-url="/login" className="btn btn-secondary btn-block">Login</a>
                </div>
            );
        } else {
            content = (
                <form method="post" onSubmit={this.validate} noValidate>
                    {this.renderErrors()}
                    <InputField name="new_password1" displayName="Password" type="password" ref="new_password1" value={this.getModel().get('new_password1')}/>
                    <InputField name="new_password2" displayName="Re-enter Password" type="password" ref="new_password2" value={this.getModel().get('new_password2')}/>
                    <div className="form-action">
                        <input type="submit" className="btn btn-secondary btn-block" value="Reset Password" />
                    </div>
                </form>
            );
        }
        return <div>{content}</div>;
    }
});

var ActivateBox = React.createBackboneClass({
    render: function() {
        return (
            <div>
                <div className="user-message">
                    Activation is complete and you are now logged in.
                </div>
                <a href="#" data-url="/" className="btn btn-secondary btn-block">Proceed To Library</a>
            </div>
        );
    }
});

var AccountHeader = React.createBackboneClass({
    navItems: [
        { id: 'account', name: 'Account', url: '/account' },
        { id: 'keys', name: 'Keys & Authentication', url: '/keys' },
        { id: 'billing', name: 'Billing', url: '/billing' }
    ],

    render: function() {
        return (
            <div>
                <DropdownMenu selected="account" />
                <HeaderMenu items={this.navItems} selected={this.props.selected} />
            </div>
        );
    }
});

var AccountScreen = React.createBackboneClass({
    render: function() {
        return (
            <div>
                <div className="navbar">
                    <AccountHeader selected="account" />
                </div>
                <div className="container">
                    <h3 className="text-center font-100">Edit your profile settings</h3>
                    <div className="content-block">
                        <form>
                            <div className="form-group">
                                <label htmlFor="username">Username</label>
                                <input type="text" className="form-control" id="username" />
                            </div>
                            <div className="form-group">
                                <label htmlFor="email">Email</label>
                                <input type="email" className="form-control" id="email" />
                            </div>
                            <div className="form-group">
                                <label htmlFor="organization">Organization</label>
                                <input type="text" className="form-control" id="organization" />
                            </div>
                            <hr />
                            <div className="form-group">
                                <label htmlFor="old-password">Old Password</label>
                                <input type="password" className="form-control" id="old-password" />
                            </div>
                            <div className="form-group">
                                <label htmlFor="new-password">New Password</label>
                                <input type="password" className="form-control" id="new-password" />
                            </div>
                            <div className="form-group">
                                <label htmlFor="password">Confirm Password</label>
                                <input type="password" className="form-control" id="password" />
                            </div>
                            <div className="form-action">
                                <button type="submit" className="btn btn-secondary">Submit</button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        );
    }
});

var KeysScreen = React.createBackboneClass({
    render: function() {
        return (
            <div>
                <div className="navbar">
                    <AccountHeader selected="keys" />
                </div>
                <div className="container">
                    <h3 className="text-center font-100">API Access Keys</h3>
                    <div className="content-block">
                        <div className="form-group">
                            <label htmlFor="username">Your API Key</label>
                            <a href="#" className="pull-right">Request a new API key</a>
                            <input type="text" className="form-control" id="username" readOnly defaultValue="f2f1517443927a88ce64e13d4e811b4ce73fb93f" />
                            <p className="help-block text-center"><i className="rf-icon-gears" /> Use your API Key to access the <a href="#">Raster Foundry API</a></p>
                        </div>
                    </div>
                </div>
            </div>
        );
    }
});

var BillingScreen = React.createBackboneClass({
    render: function() {
        return (
            <div>
                <div className="navbar">
                    <AccountHeader selected="billing" />
                </div>
                <div className="container">
                    <h3 className="text-center font-100">Billing &amp; Plan</h3>
                    <div className="content-block">
                        <p>
                            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus porta aliquam turpis. Vivamus blandit maximus augue sed auctor. Maecenas non nibh congue, molestie est quis, pellentesque mi. Nulla id euismod mi. Curabitur sagittis, ligula ut porttitor lacinia, felis lectus mollis dui, quis ultrices ipsum quam sit amet turpis. Integer eu dolor ac odio auctor mollis. Suspendisse diam justo, facilisis sed ornare volutpat, tempor quis lacus.
                        </p>
                    </div>
                </div>
            </div>
        );
    }
});

module.exports = {
    AccountScreen: AccountScreen,
    ActivateBox: ActivateBox,
    BillingScreen: BillingScreen,
    ForgotBox: ForgotBox,
    KeysScreen: KeysScreen,
    LoginBox: LoginBox,
    SendActivationBox: SendActivationBox,
    ResetPasswordBox: ResetPasswordBox,
    SignUpBox: SignUpBox,
    UserScreen: UserScreen
};
