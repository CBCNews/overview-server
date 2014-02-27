define [ 'backbone', 'apps/DocumentSetList/views/TreeJobView' ], (Backbone, TreeJobView) ->
  describe 'apps/DocumentSetList/views/TreeJobView', ->
    $el = undefined
    model = undefined
    view = undefined

    beforeEach ->
      $el = $("""
        <div class="tree-job-view">
          <h6>A new clustering</h6>
          <span class="status-description">status1</span>
        </div>
      """)
      model = new Backbone.Model(id: 10)
      view = new TreeJobView(model: model, el: $el.get(0))

    afterEach ->
      view?.remove()

    it 'should update the status description', ->
      model.set(statusDescription: 'status2')
      expect(view.$('.status-description').text()).toEqual('status2')

    it 'should add a progress bar when fractionComplete comes in', ->
      model.set(fractionComplete: 0.25)
      expect(view.$('progress').prop('value')).toEqual(0.25)

    it 'should remove a progress bar when fractionComplete becomes 1', ->
      model.set(fractionComplete: 1)
      expect(view.$('progress').length).toEqual(0)

    it 'should modify a progress bar after adding it', ->
      model.set(fractionComplete: 0.25)
      model.set(fractionComplete: 0.3)
      expect(view.$('progress').length).toEqual(1)
      expect(view.$('progress').prop('value')).toEqual(0.3)

    it 'should modify the state', ->
      model.set(state: 'error')
      expect(view.$el).toHaveClass('error')
