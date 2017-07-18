const shared = angular.module('core.shared', []);

// auth
require('./services/auth.service')(shared);
require('./services/token.service')(shared);
require('./services/user.service')(shared);

// settings
require('./services/config.provider')(shared);
require('./services/storage.service')(shared);
require('./services/status.service')(shared);
require('./services/featureFlagOverrides.service')(shared);
require('./services/featureFlags.provider')(shared);
require('./services/featureFlag.directive')(shared);

// 3rd party
require('./services/rollbarWrapper.service')(shared);
require('./services/dropbox.service')(shared);
require('./aws-sdk-s3.module.js');

// projects
require('./services/colorCorrect.service')(shared);
require('./services/datasource.service')(shared);
require('./services/project.service')(shared);
require('./services/histogram.service')(shared);
require('./services/aoi.service')(shared);
require('./services/export.service')(shared);

// scenes
require('./services/scene.service')(shared);
require('./services/thumbnail.service')(shared);
require('./services/upload.service')(shared);

// tool
require('./services/tool.service')(shared);
require('./services/toolCategory.service')(shared);
require('./services/toolTag.service')(shared);
require('./services/labUtils.service')(shared);

// map
require('./services/map.service')(shared);
require('./services/mapUtils.service')(shared);
require('./services/gridLayer.service')(shared);
require('./services/imageOverlay.service')(shared);
require('./services/layer.service')(shared);
require('./services/geocode.service')(shared);

// ui
require('./services/mousetip.service')(shared);
require('./services/feed.service')(shared);
require('./services/intercom.service')(shared);
require('./services/rollbarWrapper.service')(shared);
require('./services/upload.service')(shared);
require('./services/status.service')(shared);
require('./services/histogram.service')(shared);
require('./services/dropbox.service')(shared);
require('./services/aoi.service')(shared);
require('./services/export.service')(shared);
require('./services/featureFlagOverrides.service')(shared);
require('./services/featureFlags.provider')(shared);
require('./services/featureFlag.directive')(shared);


export default shared;
