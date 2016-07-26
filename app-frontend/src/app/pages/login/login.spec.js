import login from './login.module';

describe('login', () => {

    describe('LoginController', () => {
        let ctrl;

        beforeEach(() => {
            angular.mock.module(login);

            angular.mock.inject(($controller) => {
                ctrl = $controller('LoginController', {});
            });
        });

        it('should contain the test value', () => {
            expect(ctrl.testVal).toBe('this is a test');
        });

        it('should import media assets', () => {
            expect(ctrl.videom4v).toBeDefined();
            expect(ctrl.videoogg).toBeDefined();
            expect(ctrl.videowebm).toBeDefined();
            expect(ctrl.ffcSpace).toBeDefined();
            expect(ctrl.assetLogo).toBeDefined();
        });
    });
});
