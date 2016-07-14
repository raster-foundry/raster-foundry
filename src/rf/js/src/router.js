'use strict';

var Backbone = require('../shim/backbone'),
    _ = require('underscore');

var AppRouter = Backbone.Marionette.AppRouter.extend({
    // Key of last route context.
    _previousRouteName: null,

    // Map of { routeName: { controller: [Object], methodName: [String] }, ... }
    _routeContext: null,

    initialize: function() {
        this._routeContext = {};
    },

    // Update the address bar URL and trigger routing.
    go: function(fragment) {
        this.navigate(fragment, {trigger: true});
    },

    addRoute: function(route, controller, methodName) {
        var routeName = route.toString(),
            cb = controller[methodName];

        this._routeContext[routeName] = {
            controller: controller,
            methodName: methodName
        };

        this.route(route, routeName, cb);
    },

    execute: function(cb, args, routeName) {
        var context = this._routeContext[routeName],
            prepare = this.getSuffixMethod(context, 'Prepare');

        if (this._previousRouteName) {
            var prevContext = this._routeContext[this._previousRouteName],
                cleanUp = this.getSuffixMethod(prevContext, 'CleanUp');
            cleanUp.apply(prevContext.controller, args);
        }

        prepare.apply(context.controller, args);
        cb.apply(context.controller, args);

        this._previousRouteName = routeName;
    },

    getSuffixMethod: function(context, suffix) {
        var methodName = context.methodName + suffix,
            cb = context.controller[methodName];
        return cb ? cb : _.identity;
    }
});

var router = new AppRouter();

module.exports = {
    router: router,
    AppRouter: AppRouter
};
