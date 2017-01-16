/* global $, _ */

export default (app) => {
    class MousetipService {
        constructor($compile, $document) {
            this.$compile = $compile;
            this.$document = $document;
            this.throttledReposition = _.throttle(this.setPosition.bind(this), 16, {leading: true});
        }

        set(content) {
            if (!this.isRendered || content !== this.content) {
                this.content = content;
                this.render();
            }
            return this;
        }

        remove() {
            this.$el.toggle(false);
            this.$el.remove();
            this.$el = null;
            this.isRendered = false;
            this.removeListeners();
            return this;
        }

        render() {
            if (this.$el) {
                this.$el.remove();
            }
            this.$el = this.compileTemplate();
            this.$document.find('body').append(this.$el);
            this.isRendered = true;
            this.attachListeners();
            return this;
        }

        setPosition(e) {
            if (e && this.$el && !this.mouseOut) {
                this.$el.css({
                    top: e.pageY,
                    left: e.pageX
                });
                this.$el.toggle(true);
            } else if (this.$el) {
                this.$el.toggle(false);
            }
            return this;
        }

        onMouseOut(e) {
            if (!e.toElement) {
                this.mouseOut = true;
                if (this.$el) {
                    this.$el.toggle(false);
                }
            }
            return this;
        }

        onMouseEnter() {
            this.mouseOut = false;
            if (this.$el) {
                this.$el.toggle(true);
            }
            return this;
        }

        attachListeners() {
            $(this.$document).on('mousemove', this.throttledReposition);
            $(this.$document).on('mouseout blur', this.onMouseOut.bind(this));
            $(this.$document).on('mouseenter focus', this.onMouseEnter.bind(this));
            return this;
        }

        removeListeners() {
            $(this.$document).off('mousemove', this.throttledReposition);
            $(this.$document).off('mouseout blur', this.onMouseOut.bind(this));
            $(this.$document).off('mouseenter focus', this.onMouseEnter.bind(this));
            return this;
        }

        compileTemplate() {
            let template = `<div class="mousetip">${this.content}</div>`;
            return this.$compile(template)(this);
        }
    }

    app.service('mousetipService', MousetipService);
};
