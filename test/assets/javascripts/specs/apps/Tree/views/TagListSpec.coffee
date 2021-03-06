define [
  'jquery'
  'underscore'
  'backbone'
  'apps/Tree/views/TagList'
  'i18n'
], ($, _, Backbone, TagList, i18n) ->
  makeModel = (name="name", options={}) ->
    new Backbone.Model(_.extend({ name: name }, options))

  describe 'apps/Tree/views/TagList', ->
    collection = undefined
    view = undefined

    beforeEach ->
      i18n.reset_messages({
        'views.Tree.show.tag_list.preamble': 'preamble'
        'views.Tree.show.tag_list.export': 'export'
        'views.Tree.show.tag_list.remove': 'remove'
        'views.Tree.show.tag_list.remove.confirm': 'remove.confirm,{0},{1}'
        'views.Tree.show.tag_list.submit': 'submit'
        'views.Tree.show.tag_list.tag_name.placeholder': 'tag_name.placeholder'
        'views.Tree.show.tag_list.compound_n_documents_html': 'compound_n_documents_html,{0},{1},{2},{3}'
        'views.Tree.show.tag_list.th.count_in_tree': 'th.count_in_tree'
        'views.Tree.show.tag_list.th.count_in_docset': 'th.count_in_docset'
        'views.Tree.show.tag_list.th.count': 'th.count'
        'views.Tree.show.tag_list.th.name': 'th.name'
        'views.Tree.show.tag_list.n_documents': 'n_documents,{0}'
      })

    afterEach ->
      view?.remove()
      view?.off()

    describe 'starting with no tags', ->
      beforeEach ->
        collection = new Backbone.Collection([])
        view = new TagList({
          collection: collection
        })

      it 'should render an empty list', ->
        expect(view.$('tbody').length).toEqual(1)

      it 'should render a "new" form', ->
        expect(view.$('tfoot form').length).toEqual(1)

      it 'should render list items on reset', ->
        collection.reset([ makeModel() ])
        expect(view.$('tbody tr').length).toEqual(1)

      it 'should trigger add', ->
        spy = jasmine.createSpy()
        view.on('add', spy)
        $form = view.$('tfoot form')
        $form.find('input[name=name]').val('new tag')
        $form.submit()
        expect(spy).toHaveBeenCalledWith({ name: 'new tag', color: undefined })

      it 'should strip spaces from tag names', ->
        spy = jasmine.createSpy()
        view.on('add', spy)
        view.$('tfoot input[name=name]').val(' new tag ')
        view.$('tfoot form').submit()
        expect(spy).toHaveBeenCalledWith({ name: 'new tag', color: undefined })

      describe 'adding empty tags', ->
        # XXX these tests mimic tests in InlineTagListSpec
        it 'should not trigger add for an empty tag name', ->
          spy = jasmine.createSpy()
          view.on('add', spy)
          view.$('tfoot input[name=name]').val('')
          view.$('tfoot form').submit()
          expect(spy).not.toHaveBeenCalled()

        it 'should not trigger add for an only-spaces tag name', ->
          spy = jasmine.createSpy()
          view.on('add', spy)
          view.$('tfoot input[name=name]').val(' ')
          view.$('tfoot form').submit()
          expect(spy).not.toHaveBeenCalled()

        it 'should focus the input field', ->
          $input = view.$('tfoot input[name=name]')
          $input.val('')
          $('body').append(view.el) # make focusing work
          view.$('tfoot form').submit()
          expect($input).toBeFocused()

      it 'should not show an export link', ->
        view?.remove()
        view?.off()
        view = new TagList({
          collection: collection
          exportUrl: 'https://example.org'
        })
        expect(view.$('a.export').length).toEqual(0)

    describe 'starting with two tags', ->
      beforeEach ->
        collection = new Backbone.Collection([ makeModel('tag10', { color: '#abcdef', size: 10, sizeInTree: 10 }), makeModel('tag20') ])
        view = new TagList({
          collection: collection
          exportUrl: 'https://example.org'
        })

      it 'should add a tag to the end of the list', ->
        collection.add(makeModel('tag30'))
        expect(view.$('tbody tr:eq(2)').html()).toContain('tag30')

      it 'should add a tag to the beginning of the list', ->
        collection.add([makeModel('tag05')], { at: 0 })
        expect(view.$('tbody tr:eq(0)').html()).toContain('tag05')

      it 'should add a tag to the middle of the list', ->
        collection.add([makeModel('tag15')], { at: 1 })
        expect(view.$('tbody tr:eq(1)').html()).toContain('tag15')

      it 'should remove a tag', ->
        collection.remove(collection.first())
        expect(view.$('tbody tr:eq(0)').html()).toContain('tag20')

      it 'should remove Spectrum when deleting a tag', ->
        # This will be 0 if we remove Spectrum; remove this test if that happens
        expect($('.sp-container').length).toEqual(2)
        collection.remove(collection.first())
        expect($('.sp-container').length).toEqual(1)

      it 'should remove Spectrum in remove()', ->
        view.remove()
        view.off()
        view.$el.remove()
        view = undefined
        expect($('.sp-container').length).toEqual(0)

      it 'should change a tag', ->
        collection.first().set({
          name: 'tag11'
          color: '#111111'
        })
        $tr = view.$('tbody tr:eq(0)')
        expect($tr.find('input[name=name]').val()).toEqual('tag11')
        expect($tr.find('input[name=color]').val()).toEqual('#111111')

      it 'should render a tag without size as size 0', ->
        # https://github.com/overview/overview-server/issues/568
        collection.first().set(size: null, sizeInTree: null)
        view.render()
        expect(view.$('tbody tr:eq(0) td.count').text()).toEqual('n_documents,0')

      it 'should not render a separate count for just the tree', ->
        expect(view.$('th.tree-count').length).toEqual(0)
        expect(view.$('th.count').text()).toEqual('th.count')
        expect(view.$('td.tree-count').length).toEqual(0)

      it 'should render a separate tree-count when at least one tag has a different value', ->
        collection.first().set(size: 10, sizeInTree: 5)
        view.render()
        expect(view.$('th.tree-count').text()).toEqual('th.count_in_tree')
        expect(view.$('th.count').text()).toEqual('th.count_in_docset')
        expect(view.$('td.tree-count').length).toEqual(2)

      it 'should not change a tag when interacting', ->
        collection.first().set(
          { name: 'tag11', color: '#111111' },
          { interacting: true }
        )
        $tr = view.$('tbody tr:eq(0)')
        expect($tr.find('input[name=name]').val()).toEqual('tag10')
        expect($tr.find('input[name=color]').val()).toEqual('#abcdef')

      it 'should change a tag ID', ->
        collection.first().set({ id: 3 })
        expect(view.$('tbody tr:eq(0) input[name=id]').val()).toEqual('3')

      it 'should change a tag id even when interacting', ->
        collection.first().set({ id: 3 }, { interacting: true })
        expect(view.$('tbody tr:eq(0) input[name=id]').val()).toEqual('3')

      it 'should show an export link', ->
        view?.remove()
        view?.off()
        view = new TagList({
          collection: collection
          exportUrl: 'https://example.org'
        })
        expect(view.$('a.export').attr('href')).toEqual('https://example.org')

      it 'should trigger update when changing fields', ->
        tag = undefined
        attrs = undefined
        view.once('update', (v1, v2) -> tag = v1; attrs = v2)
        $form = view.$('form:eq(0)')
        $input = $form.find('input[name=name]')
        $input.val('foobar')
        $input.change()
        expect(tag).toBeDefined()
        expect(tag.cid).toEqual(collection.first().cid)
        expect(tag.get('name')).toEqual('tag10')
        expect(attrs).toBeDefined()
        expect(attrs.name).toEqual('foobar')

      it 'should not leave the page when pressing Enter', ->
        # Normally, a submit would crash the whole test suite.
        # So if nothing happens here, we're okay
        view.$('form:eq(0)').submit()
        expect(1).toEqual(1)

      it 'should trigger remove', ->
        spy = jasmine.createSpy()
        view.on('remove', spy)
        spyOn(window, 'confirm').and.returnValue(true)
        view.$('tbody tr:eq(0) a.remove').click()
        expect(spy).toHaveBeenCalled()

      it 'should not trigger remove if not confirmed', ->
        spy = jasmine.createSpy()
        view.on('remove', spy)
        spyOn(window, 'confirm').and.returnValue(false)
        view.$('tbody tr:eq(0) a.remove').click()
        expect(spy).not.toHaveBeenCalled()

      it 'should confirm remove with the tag name and count', ->
        spyOn(window, 'confirm').and.returnValue(false)
        view.$('tbody tr:eq(0) a.remove').click()
        expect(window.confirm).toHaveBeenCalledWith("remove.confirm,tag10,10")
