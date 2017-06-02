import angular from 'angular';

import FeatureFlagOverridesComponent from './featureFlagOverrides.component.js';
import FeatureFlagOverridesController from './featureFlagOverrides.controller.js';

const FeatureFlagOverridesModule = angular.module('components.featureFlagOverrides', []);

FeatureFlagOverridesModule.component(
    'rfFeatureFlagOverrides',
    FeatureFlagOverridesComponent
);
FeatureFlagOverridesModule.controller(
    'FeatureFlagOverridesController',
    FeatureFlagOverridesController
);

export default FeatureFlagOverridesModule;
