'use strict';

var React = require('react');

var LayerStatusComponent = React.createBackboneClass({
    render: function() {
        var checkClass = 'rf-icon-check',
            spinnerClass = 'rf-icon-loader animate-spin',
            uploadingClass = spinnerClass,
            processingClass = spinnerClass;

        if (!this.getModel().isUploading()) {
            uploadingClass = checkClass;
        }
        if (!this.getModel().isProcessing()) {
            processingClass = checkClass;
        }

        return (
            <div className="list-group-item">
              <div className="list-group-content">
                <h5>{this.getModel().get('name')}</h5>
                <ol>
                  <li>Uploading Images <i className={uploadingClass} /></li>
                  <li>Processing Tiles <i className={processingClass} /></li>
                  {/* There may be need for more steps. */}
                </ol>
              </div>
              <div className="list-group-tool">
                <a href="#" className="text-danger">Cancel</a>
              </div>
            </div>
        );
    }
});

var ProcessingBlock = React.createBackboneClass({
    render: function() {
        if (this.getCollection().length === 0) {
            return null;
        }
        return (
          <div className="processing-block">
            <a className="block-title" role="button" data-toggle="collapse" href="#processing-content" aria-expanded="false" aria-controls="processing-content">
              <h5>Processing Layers</h5>
            </a>
            <div className="collapse" id="processing-content">
              <div className="list-group">
                {this.getCollection().map(function(layer) {
                    return <LayerStatusComponent model={layer} key={layer.cid} />;
                })}
              </div>
            </div>
          </div>
        );
    }
});

module.exports = ProcessingBlock;
