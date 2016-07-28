import AccountModule from './account.module';
/* globals describe beforeEach it expect */
/* eslint max-nested-callbacks: 0 */


describe('pages.account', () => {
    describe('AccountController', () => {
        let ctrl;

        beforeEach(() => {
            angular.mock.module(AccountModule.name);

            angular.mock.inject(
                ($controller) => {
                    ctrl = $controller('AccountController', {});
                }
            );
        });

        it('should exist', () => {
            expect(ctrl).toBeDefined();
        });
    });
});
