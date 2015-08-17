'use strict';

var Backbone = require('../shim/backbone'),
    $ = require('jquery'),
    _ = require('underscore');


var AppRouter = Backbone.Marionette.AppRouter.extend({
    // Key of last route context.
    _previousRouteName: null,

    // Map of { routeName: { controller: [Object], methodName: [String] }, ... }
    _routeContext: null,

    // Promise chain to ensure everything triggers in order even if there
    // are animations in the setUp/tearDown methods.
    _sequence: null,

    initialize: function() {
        this._routeContext = {};
        this._sequence = $.Deferred().resolve().promise();
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
            this._sequence = this._sequence.then(function() {
                return cleanUp.apply(null, args);
            });
        }

        var self = this;
        this._sequence = this._sequence.then(function() {
            var result = prepare.apply(null, args);
            // Assume result is a promise if an object is returned.
            if (_.isObject(result)) {
                self._previousRouteName = routeName;
                return result.then(function() {
                    cb.apply(null, args);
                });
            } else if (result !== false) {
                // Only execute the route callback if prepare
                // did not return false.
                self._previousRouteName = routeName;
                cb.apply(null, args);
            } else {
                // Don't execute the route function
                // and cancel the route transition
                return false;
            }
        });
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
