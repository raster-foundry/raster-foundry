const shared = angular.module('core.shared', []);

// auth
require('./services/auth/auth.service')(shared);
require('./services/auth/token.service')(shared);
require('./services/auth/user.service')(shared);

// settings
require('./services/settings/config.provider')(shared);
require('./services/settings/storage.service')(shared);
require('./services/settings/status.service')(shared);
require('./services/settings/featureFlagOverrides.service')(shared);
require('./services/settings/featureFlags.provider')(shared);
require('./services/settings/featureFlag.directive')(shared);

// vendor
require('./services/vendor/rollbarWrapper.service')(shared);
require('./services/vendor/dropbox.service')(shared);
require('./services/vendor/intercom.service')(shared);
require('./services/vendor/aws-sdk-s3.module.js');

// projects
require('./services/projects/colorCorrect.service')(shared);
require('./services/projects/project.service')(shared);
require('./services/projects/histogram.service')(shared);
require('./services/projects/aoi.service')(shared);
require('./services/projects/export.service')(shared);
require('./services/projects/histogram.service')(shared);
require('./services/projects/export.service')(shared);
require('./services/projects/aoi.service')(shared);

// scenes
require('./services/scenes/scene.service')(shared);
require('./services/scenes/upload.service')(shared);
require('./services/scenes/datasource.service')(shared);

// tools
require('./services/tools/tool.service')(shared);
require('./services/tools/toolCategory.service')(shared);
require('./services/tools/toolTag.service')(shared);
require('./services/tools/labUtils.service')(shared);

// map
require('./services/map/map.service')(shared);
require('./services/map/mapUtils.service')(shared);
require('./services/map/gridLayer.service')(shared);
require('./services/map/imageOverlay.service')(shared);
require('./services/map/layer.service')(shared);
require('./services/map/geocode.service')(shared);

// common
require('./services/common/mousetip.service')(shared);
require('./services/common/feed.service')(shared);
require('./services/common/thumbnail.service')(shared);


export default shared;
