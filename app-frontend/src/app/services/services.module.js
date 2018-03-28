const shared = angular.module('services', []);

// analyses
require('./analysis/analysis.service')(shared);
require('./analysis/labUtils.service')(shared);
require('./analysis/reclassify.service')(shared);
require('./analysis/histogram.service')(shared);
require('./tools/toolTag.service')(shared);

// auth
require('./auth/auth.service')(shared);
require('./auth/token.service')(shared);
require('./auth/user.service')(shared);

// settings
require('./settings/config.provider')(shared);
require('./settings/storage.service')(shared);
require('./settings/status.service')(shared);
require('./settings/featureFlagOverrides.service')(shared);
require('./settings/featureFlags.provider')(shared);
require('./settings/featureFlag.directive')(shared);

// vendor
require('./vendor/rollbarWrapper.service')(shared);
require('./vendor/dropbox.service')(shared);
require('./vendor/intercom.service')(shared);
require('./vendor/planetLabs.service.js')(shared);
require('./vendor/aws-sdk-s3.module.js');

// projects
require('./projects/colorCorrect.service')(shared);
require('./projects/colorScheme.service')(shared);
require('./projects/project.service')(shared);
require('./projects/edit.service')(shared);
require('./projects/histogram.service')(shared);
require('./projects/aoi.service')(shared);
require('./projects/export.service')(shared);
require('./projects/histogram.service')(shared);
require('./projects/export.service')(shared);
require('./projects/aoi.service')(shared);

// scenes
require('./scenes/scene.service')(shared);
require('./scenes/upload.service')(shared);
require('./scenes/datasource.service')(shared);
require('./scenes/rasterFoundryRepository.service')(shared);
require('./scenes/planetRepository.service')(shared);
require('./scenes/cmrRepository.service')(shared);
require('./scenes/datasourceLicense.service')(shared);

// map
require('./map/map.service')(shared);
require('./map/mapUtils.service')(shared);
require('./map/gridLayer.service')(shared);
require('./map/imageOverlay.service')(shared);
require('./map/layer.service')(shared);
require('./map/geocode.service')(shared);

// vectors
require('./vectors/shapes.service.js')(shared);

// common
require('./common/mousetip.service')(shared);
require('./common/feed.service')(shared);
require('./common/thumbnail.service')(shared);
require('./common/decimal.filter')(shared);
require('./common/modal.service')(shared);
require('./common/url.filter')(shared);


export default shared;
