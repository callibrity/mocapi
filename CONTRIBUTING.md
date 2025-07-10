# Contributing to Mocapi

Thanks for your interest in contributing to Mocapi! We welcome pull requests, issues, and feedback from the community.

## How to Contribute

### 🐛 Reporting Bugs

If you find a bug, please [open an issue](https://github.com/callibrity/mocapi/issues/new) and include the following:

- A clear description of the problem
- Steps to reproduce the issue
- Expected vs actual behavior
- Version of Mocapi and relevant environment details

### 💡 Requesting Features

We’re happy to hear your ideas! Before opening a feature request, please check if one already exists. If not, open a new issue and include:

- A description of the proposed feature
- Why it would be useful
- Any relevant use cases or examples

### 🔧 Submitting a Pull Request

If you’d like to contribute code:

1. **Fork** the repository and create a new branch from `main`.
2. Make your changes, writing tests if applicable.
3. Run the build and tests to make sure everything passes:

   ```bash
   mvn verify
   ```

4. Open a pull request and describe your changes.

Please follow idiomatic Java practices and keep your code clean and well-documented. For larger changes, consider opening an issue first to discuss the approach.

## 🧱 Project Structure

- `mocapi-spring-boot-starter`: Core Spring Boot auto-configuration and endpoint registration
- `mocapi-tools`: Tool support for building MCP tools via `@ToolService`
- `mocapi-prompts`: Prompt support for building structured prompt handlers via `@PromptService`
- `examples/`: Sample applications and usage demonstrations

## 📜 Code Style and Conventions

- Use Java 23+ features judiciously — prefer clarity and simplicity
- Format using the default IntelliJ Java conventions or Google Java Style
- Public methods and classes should be Javadoc'd
- Keep method visibility as narrow as possible

## 🙌 Community Standards

We strive to foster a welcoming and respectful community. By participating, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## 📄 License

By contributing to this project, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

---

### 🧰 Helpful Resources

- **GitHub’s official guide:**  
  [https://docs.github.com/en/github/building-a-strong-community/setting-guidelines-for-repository-contributors](https://docs.github.com/en/github/building-a-strong-community/setting-guidelines-for-repository-contributors)

- **Open Source Guides: Contributing.md**  
  [https://opensource.guide/how-to-contribute/#setting-guidelines](https://opensource.guide/how-to-contribute/#setting-guidelines)

---