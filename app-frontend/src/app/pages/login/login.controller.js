import assetLogo from '../../../assets/images/logo-raster-foundry.png';
import ffcSpace from '../../../assets/images/ffc-space.png';
import videom4v from '../../../assets/video/ffc-space.m4v';
import videoogg from '../../../assets/video/ffc-space.ogg';
import videowebm from '../../../assets/video/ffc-space.webm';

class LoginController {
    constructor($log) {
        'ngInject';

        $log.debug('LoginController initialized');
        this.videom4v = videom4v;
        this.videoogg = videoogg;
        this.videowebm = videowebm;
        this.ffcSpace = ffcSpace;
        this.assetLogo = assetLogo;
        this.testVal = 'this is a test';
    }
}

export default LoginController;
