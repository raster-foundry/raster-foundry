import loginModule from './login.module';
/* globals describe beforeEach it expect */
/* eslint max-nested-callbacks: 0 */


describe('pages.login', () => {
    describe('LoginController', () => {
        let ctrl;

        beforeEach(() => {
            angular.mock.module(loginModule.name);

            angular.mock.inject(
                ($controller) => {
                    ctrl = $controller('LoginController', {});
                }
            );
        });

        it('should exist', () => {
            expect(ctrl).toBeDefined();
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
