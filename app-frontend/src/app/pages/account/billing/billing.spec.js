import BillingModule from './billing.module';
/* globals describe beforeEach it expect */
/* eslint max-nested-callbacks: 0 */


describe('pages.billing', () => {
    describe('BillingController', () => {
        let ctrl;

        beforeEach(() => {
            angular.mock.module(BillingModule.name);

            angular.mock.inject(
                ($controller) => {
                    ctrl = $controller('BillingController', {});
                }
            );
        });

        it('should exist', () => {
            expect(ctrl).toBeDefined();
        });
    });
});
