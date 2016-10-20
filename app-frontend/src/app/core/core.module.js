const shared = angular.module('core.shared', []);

require('./services/scene.service')(shared);
require('./services/bucket.service')(shared);
require('./services/user.service')(shared);
require('./services/config.provider')(shared);
require('./services/auth.service')(shared);

export default shared;
