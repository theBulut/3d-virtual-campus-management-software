// The browser side of the bridge. Unity calls these through DllImport("__Internal").
//
// The page owns the session: it already refreshes and rotates tokens, and a second implementation in C#
// would be a second set of rules to keep in step (docs/DECISIONS.md D-44). The game therefore never
// stores a token — it asks for the current one whenever it needs to call the API.
mergeInto(LibraryManager.library, {

  // Returns the current access token. Unity frees the buffer, so it has to be allocated on the heap.
  CampusRequestToken: function () {
    var token = (window.campusBridge && window.campusBridge.requestToken()) || '';
    var size = lengthBytesUTF8(token) + 1;
    var buffer = _malloc(size);
    stringToUTF8(token, buffer, size);
    return buffer;
  },

  // Tells the page that the scene has been built, so it can take down its loading indicator.
  CampusSceneReady: function () {
    if (window.campusBridge && window.campusBridge.onSceneReady) {
      window.campusBridge.onSceneReady();
    }
  },

  // The API base. Same origin as the page, so the request carries no CORS preflight.
  CampusApiBase: function () {
    var base = window.location.origin + '/api';
    var size = lengthBytesUTF8(base) + 1;
    var buffer = _malloc(size);
    stringToUTF8(base, buffer, size);
    return buffer;
  },
});
