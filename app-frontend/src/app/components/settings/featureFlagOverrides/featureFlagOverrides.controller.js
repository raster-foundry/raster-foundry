export default class FeatureFlagOverrideController {
    constructor(featureFlags) {
        'ngInject';
        this.featureFlags = featureFlags;
        this.flags = featureFlags.get();
    }
}
