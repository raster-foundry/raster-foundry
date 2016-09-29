const shared = angular.module('core.shared', []);

require('./services/scene.service')(shared);

export default shared;
