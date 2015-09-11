'use strict';

var $ = require('jquery'),
    React = require('react'),
    router = require('../router').router,
    settings = require('../settings'),
    models = require('./models'),
    components = require('./components');

function showHomeIfLoggedIn() {
    var user = settings.getUser();
    if (user.isAuthenticated()) {
        router.go('/');
        return true;
    }
    return false;
}

function loginRequired(fn) {
    return function() {
        var user = settings.getUser();
        if (!user.isAuthenticated()) {
            router.go('/login');
        } else {
            fn.apply(this, arguments);
        }
    };
}

var UserController = {
    account: loginRequired(function() {
        this.renderComponent(<components.AccountScreen />);
    }),

    billing: loginRequired(function() {
        this.renderComponent(<components.BillingScreen />);
    }),

    keys: loginRequired(function() {
        this.renderComponent(<components.KeysScreen />);
    }),

    login: function() {
        if (showHomeIfLoggedIn()) {
            return;
        }
        var model = new models.LoginFormModel();
        this.renderComponent(<components.LoginScreen model={model} />);
    },

    signUp: function() {
        var model = new models.SignUpFormModel();
        this.renderComponent(<components.SignUpScreen model={model} />);
    },

    sendActivation: function() {
        var model = new models.ResendFormModel();
        this.renderComponent(<components.SendActivationScreen model={model} />);
    },

    forgot: function() {
        var model = new models.ForgotFormModel();
        this.renderComponent(<components.ForgotScreen model={model} />);
    },

    activate: function() {
        this.renderComponent(<components.ActivateScreen />);
    },

    renderComponent: function(component) {
        var el = $('#container').get(0);
        React.render(component, el);
    },

    logout: function() {
        var user = settings.getUser();
        user.logout().always(function() {
            router.go('/login');
        });
    }
};

module.exports = UserController;
