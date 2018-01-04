/* Feature Flag directive
 * Attribute directive to show or hide elements based on the presence of a feature flag
 * Additional attribute: feature-flag-hide
 *                       Hide the element in the presence of the feature flag, instead of showing it
 * Usage:
 *      <div feature-flag="some-feature-flag">
 *          ...
 *      </div>
 *
 *      <div feature-flag="some-other-flag" feature-flag-hide>
 *          ...
 *      </div>
 */
export default (app) => {
    class FeatureFlagDirective {
        constructor(featureFlags, $interpolate) {
            this.featureFlags = featureFlags;
            this.$interpolate = $interpolate;

            this.transclude = 'element';
            this.priority = 599;
            this.terminal = true;
            this.restrict = 'A';
            this.$$tlb = true;
        }

        compile(tElement, tAttrs) {
            let hasHideAttribute = 'featureFlagHide' in tAttrs;
            tElement[0].textContent = ' featureFlag: ' + tAttrs.featureFlag +
                ' is ' + (hasHideAttribute ? 'on' : 'off') + ' ';
            return (
                $scope, element, attrs, ctrl, $transclude
            ) => {
                let featureEl;
                let childScope;
                $scope.$watch(() => {
                    let featureFlag = this.$interpolate(attrs.featureFlag)($scope);
                    return this.featureFlags.isOnByDefault(featureFlag);
                }, (isEnabled) => {
                    let showElement = hasHideAttribute ? !isEnabled : isEnabled;

                    if (showElement) {
                        childScope = $scope.$new();
                        $transclude(childScope, (clone) => {
                            featureEl = clone;
                            element.after(featureEl).remove();
                        });
                    } else {
                        if (childScope) {
                            childScope.$destroy();
                            childScope = null;
                        }
                        if (featureEl) {
                            featureEl.after(element).remove();
                            featureEl = null;
                        }
                    }
                });
            };
        }

        static factory(featureFlags, $interpolate) {
            'ngInject';
            return new FeatureFlagDirective(featureFlags, $interpolate);
        }
    }

    app.directive(
        'featureFlag',
        FeatureFlagDirective.factory
    );
};
