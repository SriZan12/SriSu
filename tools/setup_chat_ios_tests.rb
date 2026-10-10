require 'xcodeproj'

# Adds an isolated native test scheme to the existing app. No production endpoint,
# device signing profile or credentials are changed by this setup.
root = File.expand_path('..', __dir__)
path = File.join(root, 'iosApp/iosApp.xcodeproj')
project = Xcodeproj::Project.open(path)
app = project.targets.find { |target| target.name == 'iosApp' }
raise 'SriSu app target missing' unless app
tests = project.targets.find { |target| target.name == 'SriSuChatTests' } || project.new_target(:unit_test_bundle, 'SriSuChatTests', :ios, '18.0')
tests.add_dependency(app) unless tests.dependencies.any? { |dependency| dependency.target == app }
reference = project.files.find { |file| file.path == 'ChatTests/ChatMediaTests.swift' } || project.main_group.new_file('ChatTests/ChatMediaTests.swift')
tests.source_build_phase.add_file_reference(reference) unless tests.source_build_phase.files_references.include?(reference)
tests.build_configurations.each do |config|
  config.build_settings.merge!(
    'SWIFT_VERSION' => '5.0', 'SWIFT_ENABLE_EXPLICIT_MODULES' => 'NO',
    'GENERATE_INFOPLIST_FILE' => 'YES', 'PRODUCT_NAME' => 'SriSuChatTests',
    'PRODUCT_BUNDLE_IDENTIFIER' => 'com.srisu.evaluation.SriSuChatTests',
    'TEST_HOST' => '$(BUILT_PRODUCTS_DIR)/SriSu.app/SriSu', 'BUNDLE_LOADER' => '$(TEST_HOST)',
    'CODE_SIGN_STYLE' => 'Automatic', 'TARGETED_DEVICE_FAMILY' => '1,2'
  )
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app)
scheme.add_test_target(tests)
scheme.save_as(path, 'SriSuChatTests', true)
