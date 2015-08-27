var Backbone = require('../../shim/backbone');

var UserModel = Backbone.Model.extend({
    defaults: {
        logged_in: false
    },

    url: '/user/login',

    // Retrieve logged_in from backend, and
    // set username if they are logged in.
    checkAuthentication: function() {
        return this.fetch();
    },

    // Both login and logout methods return jqXHR objects so that callbacks can
    // be specified upon usage. They both update the user model, so any event
    // listeners that subscribe to the `sync` event will be triggered.
    login: function(attrs) {
        return this.fetch({
            type: 'POST',
            url: '/user/login',
            data: {
                'username': attrs.username,
                'password': attrs.password
            }
        });
    },

    logout: function() {
        var jqXHR = this.fetch({
            url: '/user/logout'
        });

        var user = this;
        jqXHR.done(function() {
            // We have to unset the username manually here because when the
            // server does not return a username (because the user has been
            // logged out), our model's username is not updated and the old
            // username persists.
            // Additionally, we change this silently because the login and
            // logout functions only advertise firing a single `sync` event,
            // and this would fire an additional `change` event. We must
            // suppress this to maintain consistency in our API.
            user.unset('username', {silent: true});
        });

        return jqXHR;
    }
});

module.exports = {
    UserModel: UserModel
};
