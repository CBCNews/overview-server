define [ 'backbone', 'apps/UserAdmin/views/UsersView' ], (Backbone, UsersView) ->
  describe 'apps/UserAdmin/views/UsersView', ->
    collection = undefined
    view = undefined
    modelViews = undefined
    class MockModel extends Backbone.Model
    class MockCollection extends Backbone.Collection
      model: MockModel
    class MockView extends Backbone.View
      tagName: 'tr'
      initialize: (@options) ->
        modelViews.push(this)
      render: ->
        @$el.text(@model.get('foo'))
        @rendered = true

    beforeEach ->
      modelViews = []
      collection = new MockCollection
      view = new UsersView
        adminEmail: 'admin@example.org'
        collection: collection
        modelView: MockView
      view.render()

    afterEach ->
      view?.remove()

    it 'should create a modelView per model', ->
      collection.reset([{foo: 'bar'}, {foo: 'baz'}])
      expect(modelViews.length).toEqual(2)

    it 'should pass adminEmail to each view', ->
      collection.reset([{ foo: 'bar' }])
      expect(modelViews[0].options.adminEmail).toEqual('admin@example.org')

    it 'should render each view on reset', ->
      collection.reset([{ foo: 'bar' }, { foo: 'baz' } ])
      for v in modelViews
        expect(v.rendered).toBe(true)
      expect(view.$el.children().length).toEqual(2)

    it 'should remove a model when removed', ->
      collection.reset([{ foo: 'bar1' }, { foo: 'bar2' }, { foo: 'bar3' }])
      collection.remove(collection.at(1))
      s = view.$el.text()
      expect(s).toContain('bar1')
      expect(s).toContain('bar3')
      expect(s).not.toContain('bar2')

    it 'should add a model when added', ->
      collection.add([{ foo: 'bar' }])
      expect(view.$el.text()).toContain('bar')
