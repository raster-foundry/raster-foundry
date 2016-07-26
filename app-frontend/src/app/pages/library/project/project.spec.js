import ProjectModule from './project.module';
/* globals describe beforeEach it expect */
/* eslint max-nested-callbacks: 0 */


describe('pages.project', () => {
    describe('ProjectController', () => {
        let ctrl;

        beforeEach(() => {
            angular.mock.module(ProjectModule.name);

            angular.mock.inject(
                ($controller) => {
                    ctrl = $controller('ProjectController', {});
                }
            );
        });

        it('should exist', () => {
            expect(ctrl).toBeDefined();
        });
    });
});
