/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import _ from 'underscore';
import Marionette from 'backbone.marionette';

export default Marionette.ItemView.extend({

  collectionEvents: function () {
    return {
      'all': 'shouldRender',
      'limitReached': 'flashPagination'
    };
  },

  events: function () {
    return {
      'click .js-bulk-change': 'onBulkChangeClick',
      'click .js-reload': 'reload',
      'click .js-next': 'selectNext',
      'click .js-prev': 'selectPrev'
    };
  },

  initialize: function (options) {
    this.listenTo(options.app.state, 'change', this.render);
  },

  onRender: function () {
    this.$('[data-toggle="tooltip"]').tooltip({ container: 'body', placement: 'bottom' });
  },

  onBeforeRender: function () {
    this.$('[data-toggle="tooltip"]').tooltip('destroy');
  },

  onDestroy: function () {
    this.$('[data-toggle="tooltip"]').tooltip('destroy');
  },

  onBulkChangeClick: function (e) {
    e.preventDefault();
    this.bulkChange();
  },

  bulkChange: function () {

  },

  shouldRender: function (event) {
    if (event !== 'limitReached') {
      this.render();
    }
  },

  reload: function () {
    this.options.app.controller.fetchList();
  },

  selectNext: function () {
    this.options.app.controller.selectNext();
  },

  selectPrev: function () {
    this.options.app.controller.selectPrev();
  },

  flashPagination: function () {
    var flashElement = this.$('.search-navigator-header-pagination');
    flashElement.addClass('in');
    setTimeout(function () {
      flashElement.removeClass('in');
    }, 2000);
  },

  serializeData: function () {
    return _.extend(Marionette.ItemView.prototype.serializeData.apply(this, arguments), {
      state: this.options.app.state.toJSON()
    });
  }
});


