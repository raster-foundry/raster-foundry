import KeysModule from './keys.module';
/* globals describe beforeEach it expect */
/* eslint max-nested-callbacks: 0 */


describe('pages.keys', () => {
    describe('KeysController', () => {
        let ctrl;

        beforeEach(() => {
            angular.mock.module(KeysModule.name);

            angular.mock.inject(
                ($controller) => {
                    ctrl = $controller('KeysController', {});
                }
            );
        });

        it('should exist', () => {
            expect(ctrl).toBeDefined();
        });
    });
});
