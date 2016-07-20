import app from './app';

describe('app', () => {

  describe('AppController', () => {
    let ctrl;

    beforeEach(() => {
      angular.mock.module(app);

      angular.mock.inject(($controller) => {
        ctrl = $controller('AppController', {});
      });
    });

    it('should contain the app name', () => {
      expect(ctrl.name).toBe('Raster Foundry');
    });
  });
});
