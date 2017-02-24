/* Feature Flag Overrides service
 * Feature flags can be set / overridden in a number of ways:
 *    A) In app.config.js, feature flags are read from the /api/config endpoint and set.
 *       This is the default global source of authority for feature flags.
 *    B) In auth.service.js upon login, feature flags are read from the profile.user_metadata
 *       object and override the global defaults.
 *    C) Manual overrides are read from localstorage, and are mostly useful for development
 *       purposes.  These can be set from the /settings/profile page if the application is
 *       run in a node dev environment (IE run using `npm start` instead of loaded through nginx)
 *       These settings are persistent across page reloads and override all other sources of
 *       feature flags.
 * Note: In B and C above, feature flags must exist in the global scope, or they are ignored.
 */
export default app => {
    class FeatureFlagOverrides {
        constructor($rootElement, $log, localStorage) {
            'ngInject';
            this.localStorage = localStorage;
            this.$log = $log;

            let keyPrefix = 'featureFlags.';
            this.prefixedKeyForUser = (user) => {
                return (flagName) => keyPrefix + user + '.' + flagName;
            };
        }

        /* Sets the user to store overrides for.
           This method must be called before setting / fetching any overrides, since overrides
           are stored per user
        */
        setUser(userId) {
            this.prefixedKeyFor = this.prefixedKeyForUser(userId);
        }

        isPresent(key) {
            if (!this.prefixedKeyFor) {
                return false;
            }
            let val = this.localStorage.get(this.prefixedKeyFor(key));
            return typeof val !== 'undefined' && val !== null;
        }

        get(flagName) {
            if (!this.prefixedKeyFor) {
                this.$log.error('featureFlagOverrides.get called before setting a user.');
                return false;
            }
            return this.localStorage.get(this.prefixedKeyFor(flagName));
        }

        set(flag, value) {
            if (!this.prefixedKeyFor) {
                this.$log.error('featureFlagOverrides.set called before setting a user.');
                return;
            }
            let setFlag = (val, flagName) => {
                this.localStorage.set(this.prefixedKeyFor(flagName), val);
            };
            if (angular.isObject(flag)) {
                angular.forEach(flag, setFlag);
            } else {
                setFlag(value, flag);
            }
        }

        remove(flagName) {
            if (!this.prefixedKeyFor) {
                this.$log.error('featureFlagOverrides.remove called before setting a user.');
                return;
            }
            this.localStorage.remove(this.prefixedKeyFor(flagName));
        }
    }

    app.service('featureFlagOverrides', FeatureFlagOverrides);
};
