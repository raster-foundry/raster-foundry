import layerPage from './layer';
import aoiPage from './aoi';
import colormodePage from './colormode';
import correctionsPage from './corrections';
import exportsPage from './exports';
import exportPage from './export';
import annotationsPage from './annotations';
import annotatePage from './annotate';
import scenesPages from './scenes';

export default [
    layerPage,
    aoiPage,
    colormodePage,
    correctionsPage,
    exportsPage,
    exportPage,
    annotationsPage,
    annotatePage,
    ...scenesPages
];
