import modal from 'angular-ui-bootstrap/src/modal';
const shared = angular.module('services', [modal]);

// analyses
require('./analysis/analysis.service').default(shared);
require('./analysis/labUtils.service').default(shared);
require('./analysis/reclassify.service').default(shared);
require('./analysis/histogram.service').default(shared);
require('./tools/toolTag.service').default(shared);

// auth
require('./auth/auth.service').default(shared);
require('./auth/token.service').default(shared);
require('./auth/user.service').default(shared);
require('./auth/platform.service.js').default(shared);
require('./auth/organization.service.js').default(shared);
require('./auth/team.service.js').default(shared);
require('./auth/permissions.service.js').default(shared);

// settings
require('./settings/config.provider').default(shared);
require('./settings/storage.service').default(shared);
require('./settings/status.service').default(shared);
require('./settings/featureFlagOverrides.service').default(shared);
require('./settings/featureFlags.provider').default(shared);
require('./settings/featureFlag.directive').default(shared);

// vendor
require('./vendor/rollbarWrapper.service').default(shared);
require('./vendor/dropbox.service').default(shared);
require('./vendor/intercom.service').default(shared);
require('./vendor/planetLabs.service.js').default(shared);
require('./vendor/aws-sdk-s3.module.js');

// projects
require('./projects/colorCorrect.service').default(shared);
require('./projects/colorScheme.service').default(shared);
require('./projects/project.service').default(shared);
require('./projects/edit.service').default(shared);
require('./projects/histogram.service').default(shared);
require('./projects/aoi.service').default(shared);
require('./projects/export.service').default(shared);
require('./projects/histogram.service').default(shared);
require('./projects/export.service').default(shared);
require('./projects/aoi.service').default(shared);

// scenes
require('./scenes/scene.service').default(shared);
require('./scenes/upload.service').default(shared);
require('./scenes/datasource.service').default(shared);
require('./scenes/rasterFoundryRepository.service').default(shared);
require('./scenes/planetRepository.service').default(shared);
require('./scenes/cmrRepository.service').default(shared);
require('./scenes/datasourceLicense.service').default(shared);

// map
require('./map/map.service').default(shared);
require('./map/mapUtils.service').default(shared);
require('./map/imageOverlay.service').default(shared);
require('./map/layer.service').default(shared);
require('./map/geocode.service').default(shared);

// vectors
require('./vectors/shapes.service.js').default(shared);

// common
require('./common/mousetip.service').default(shared);
require('./common/feed.service').default(shared);
require('./common/thumbnail.service').default(shared);
require('./common/decimal.filter').default(shared);
require('./common/modal.service').default(shared);
require('./common/url.filter').default(shared);
require('./common/orgStatus.filter').default(shared);
require('./common/pagination.service').default(shared);
require('./common/graph.service').default(shared);


export default shared;
