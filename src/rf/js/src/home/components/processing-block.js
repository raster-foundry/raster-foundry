'use strict';

var React = require('react');

var LayerStatusComponent = React.createBackboneClass({
    render: function() {
        var checkClass = 'rf-icon-check',
            spinnerClass = 'rf-icon-loader animate-spin',
            failedClass = 'rf-icon-attention text-danger',
            uploadingClass = spinnerClass,
            processingClass = spinnerClass,
            actionLink = (<a href="#" className="text-danger">Cancel</a>);

        if (this.getModel().isProcessing() ||
            this.getModel().isCompleted()) {
            uploadingClass = checkClass;
        } else if (this.getModel().isFailed()) {
            uploadingClass = failedClass;
        }

        if (this.getModel().isCompleted()) {
            processingClass = checkClass;
        } else if (this.getModel().isFailed()) {
            processingClass = failedClass;
        }

        if (this.getModel().isDoneWorking()) {
            actionLink = (<a onClick={this.dismiss}>Dismiss</a>);
        }

        return (
            <div className="list-group-item">
              <div className="list-group-content">
                <h5>{this.getModel().get('name')}</h5>
                <ol>
                  <li>
                    Uploading Images <i className={uploadingClass} />
                    <ul className="notice">
                      <li><strong>fileName.tiff</strong><i className="rf-icon-attention"></i></li>
                    </ul>
                    <span className="notice">
                      <strong>Alert</strong> put anything in here and it should look fine. <i className="rf-icon-attention"></i>
                    </span>
                  </li>
                  <li>Processing Tiles <i className={processingClass} /></li>
                  {/* There may be need for more steps. */}
                </ol>
              </div>
              <div className="list-group-tool">
                { actionLink }
              </div>
            </div>
        );
    },

    dismiss: function(e) {
        e.preventDefault();
        var model = this.getModel();
        model.dismiss();
        this.props.removeItem(model);
    }
});

var ProcessingBlock = React.createBackboneClass({
    render: function() {
        var self = this;
        if (this.getCollection().length === 0) {
            return null;
        }
        return (
          <div className="processing-block">
            <a className="block-title collapsed" role="button" data-toggle="collapse" href="#processing-content" aria-expanded="false" aria-controls="processing-content">
              <h5>Processing Layers</h5>
            </a>
            <div className="collapse" id="processing-content">
              <div className="list-group">
                {this.getCollection().map(function(layer) {
                    return <LayerStatusComponent removeItem={self.removeItem} model={layer} key={layer.cid} />;
                })}
              </div>
            </div>
          </div>
        );
    },

    removeItem: function(model) {
        this.getCollection().remove(model);
    }
});

module.exports = ProcessingBlock;
