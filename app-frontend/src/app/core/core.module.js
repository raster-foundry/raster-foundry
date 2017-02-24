const shared = angular.module('core.shared', []);

require('./services/auth.service')(shared);
require('./services/colorCorrect.service')(shared);
require('./services/config.provider')(shared);
require('./services/datasource.service')(shared);
require('./services/gridLayer.service')(shared);
require('./services/imageOverlay.service')(shared);
require('./services/layer.service')(shared);
require('./services/map.service')(shared);
require('./services/mousetip.service')(shared);
require('./services/project.service')(shared);
require('./services/scene.service')(shared);
require('./services/thumbnail.service')(shared);
require('./services/token.service')(shared);
require('./services/tool.service')(shared);
require('./services/toolCategory.service')(shared);
require('./services/toolTag.service')(shared);
require('./services/user.service')(shared);
require('./services/storage.service')(shared);
require('./services/mapUtils.service')(shared);

require('./services/featureFlagOverrides.service')(shared);
require('./services/featureFlags.provider')(shared);
require('./services/featureFlag.directive')(shared);

export default shared;
