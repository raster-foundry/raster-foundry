import LibraryModule from './library.module';
/* globals describe beforeEach it expect */
/* eslint max-nested-callbacks: 0 */


describe('pages.login', () => {
    describe('LibraryController', () => {
        let ctrl;

        beforeEach(() => {
            angular.mock.module(LibraryModule.name);

            angular.mock.inject(
                ($controller) => {
                    ctrl = $controller('LibraryController', {});
                }
            );
        });

        it('should exist', () => {
            expect(ctrl).toBeDefined();
        });
    });
});
