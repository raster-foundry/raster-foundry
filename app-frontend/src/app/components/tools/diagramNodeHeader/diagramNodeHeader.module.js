import angular from 'angular';
import DiagramNodeHeaderComponent from './diagramNodeHeader.component.js';
import DiagramNodeHeaderController from './diagramNodeHeader.controller.js';

const DiagramNodeHeaderModule = angular.module(
    'components.tools.diagramNodeHeader',
    []
);

DiagramNodeHeaderModule.component(
    'rfDiagramNodeHeader', DiagramNodeHeaderComponent
);

DiagramNodeHeaderModule.controller(
    'DiagramNodeHeaderController', DiagramNodeHeaderController
);

export default DiagramNodeHeaderModule;
