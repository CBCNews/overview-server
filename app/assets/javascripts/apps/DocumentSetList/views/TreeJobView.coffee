define [
  'jquery'
  'backbone'
], ($, Backbone) ->
  class TreeJobView extends Backbone.View
    initialize: ->
      @$statusDescription = @$('.status-description')
      if !@$statusDescription.length
        throw 'You must initialize TreeJobView with an HTML element that contains a span.status-description'

      @listenTo(@model, 'change', @_onModelChange)

    render: ->
      this

    _onModelChange: (model) ->
      if 'statusDescription' of model.changed
        @$statusDescription.text(model.changed.statusDescription)

      if 'state' of model.changed
        @$el.attr('class', model.changed.state)

      if 'fractionComplete' of model.changed
        if model.changed.fractionComplete == 1
          @_removeProgressBar()
        else
          @_$progressBar().prop('value', model.changed.fractionComplete)

    _$progressBar: ->
      if !@__$progressBar?
        @__$progressBar = $('<progress></progress>')
        @$statusDescription.before(@__$progressBar)

      @__$progressBar

    _removeProgressBar: ->
      @__$progressBar?.remove()
      @__$progressBar = null
