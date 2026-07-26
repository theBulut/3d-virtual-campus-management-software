import { render, screen } from '@testing-library/react';
import App from './App';

beforeEach(() => {
  global.fetch = jest.fn(() =>
    Promise.resolve({ json: () => Promise.resolve({ status: 'UP' }) })
  );
});

test('renders heading', async () => {
  render(<App />);
  expect(
    screen.getByText(/3D Virtual Campus Management Software/i)
  ).toBeInTheDocument();
  expect(await screen.findByText('UP')).toBeInTheDocument();
});
