/* globals BUILDCONFIG */

function updateFlagsAndGetAll(newFlags) {
    let flagsList = angular.isArray(newFlags) ? newFlags : Object.values(newFlags);
    flagsList.forEach(flag => {
        this.serverFlagCache[flag.key] = flag.active;
        flag.active = this.isOnByDefault(flag.key);
    });
    angular.extend(this.flags, newFlags);
    return this.flags;
}

function updateFlagsWithPromise(promise) {
    return promise.then(value => {
        return updateFlagsAndGetAll.bind(this)(value.data || value);
    });
}

export default app => {
    class FeatureFlags {
        constructor($q, featureFlagOverrides, initialFlags) {
            this.$q = $q;
            this.serverFlagCache = {};
            this.flags = [];
            this.initialFlags = initialFlags ? initialFlags : [];
            this.perUserFlags = {};
            this.featureFlagOverrides = featureFlagOverrides;
            if (this.initialFlags.length) {
                this.set(initialFlags);
            }
        }

        set(newFlags) {
            if (angular.isArray(newFlags)) {
                let deferred = this.$q.defer();
                deferred.resolve(updateFlagsAndGetAll.bind(this)(newFlags));
                return deferred.promise;
            }
            return updateFlagsWithPromise.bind(this)(newFlags);
        }

        get() {
            return this.flags;
        }

        enable(flag) {
            flag.active = true;
            this.featureFlagOverrides.set(flag.key, true);
        }

        disable(flag) {
            flag.active = false;
            this.featureFlagOverrides.set(flag.key, false);
        }

        reset(flag) {
            flag.active = this.serverFlagCache[flag.key];
            this.featureFlagOverrides.remove(flag.key);
        }

        isOn(key) {
            return this.isOverridden(key) ?
                this.featureFlagOverrides.get(key) :
                this.serverFlagCache[key];
        }

        isOnByDefault(key) {
            return this.serverFlagCache[key];
        }

        isOverridden(key) {
            return this.featureFlagOverrides.isPresent(key);
        }

    }

    class PerUserFeatureFlagService {
        constructor($resource) {
            'ngInject';

            this.PerUserFeatureFlag = $resource(`${BUILDCONFIG.API_HOST}/feature-flags/`);
        }

        load() {
            return this.PerUserFeatureFlag.query().$promise;
        }
    }

    class FeatureFlagsProvider {
        constructor() {
            this.initialFlags = [];
        }

        setInitialFlags(flags) {
            this.initialFlags = flags;
        }

        $get($q, featureFlagOverrides) {
            'ngInject';
            return new FeatureFlags($q, featureFlagOverrides, this.initialFlags);
        }
    }

    app.service('perUserFeatureFlags', PerUserFeatureFlagService);
    app.provider('featureFlags', FeatureFlagsProvider);
};
