import {Map} from 'immutable';

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
            this.flagCache = new Map();

            let keyPrefix = 'featureFlags.';
            this.prefixedKeyForUser = (user) => {
                return (flagName) => keyPrefix + user + '.' + flagName;
            };
        }

        /* Sets the user to store overrides for.
           This method must be called before setting / fetching any overrides, since overrides
           are stored per user
        */
        setUser(profile) {
            this.prefixedKeyFor = this.prefixedKeyForUser(profile.userId);
            this.userProfile = profile;
        }

        isPresent(key) {
            if (!this.prefixedKeyFor) {
                return false;
            }
            let prefixed = this.prefixedKeyFor(key);
            let cached = this.flagCache.get(prefixed);
            if (!cached) {
                cached = this.localStorage.get(prefixed);
            }
            if (!cached) {
                let profileFlags = this.userProfile &&
                    this.userProfile.user_metadata &&
                    this.userProfile.user_metadata.featureFlags;
                cached = profileFlags ? profileFlags.find((flag) => flag.key === key) : false;
            }
            return typeof cached !== 'undefined' && cached !== null;
        }

        get(flagName) {
            if (!this.prefixedKeyFor) {
                this.$log.error('featureFlagOverrides.get called before setting a user.');
                return false;
            }
            let prefixed = this.prefixedKeyFor(flagName);
            let cached = this.flagCache.get(prefixed);
            if (!cached) {
                cached = this.localStorage.get(prefixed);
            }
            if (!cached) {
                let profileFlags = this.userProfile &&
                    this.userProfile.user_metadata &&
                    this.userProfile.user_metadata.featureFlags;
                let f = profileFlags && profileFlags.find((flag) => flag.key === flagName);
                cached = f ? f.active : false;
            }
            return cached;
        }

        set(flag, value) {
            if (!this.prefixedKeyFor) {
                this.$log.error('featureFlagOverrides.set called before setting a user.');
                return;
            }
            let setFlag = (val, flagName) => {
                let prefixed = this.prefixedKeyFor(flagName);
                this.flagCache = this.flagCache.set(prefixed, val);
                this.localStorage.set(prefixed, val);
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
            let prefixed = this.prefixedKeyFor(flagName);
            this.localStorage.remove(prefixed);
            this.flagCache = this.flagCache.delete(prefixed);
        }
    }

    app.service('featureFlagOverrides', FeatureFlagOverrides);
};
